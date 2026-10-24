package com.roomify.service;

import com.roomify.support.TestUserSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.ai.AiDesignProvider;
import com.roomify.dto.design.*;
import com.roomify.exception.*;
import com.roomify.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DesignLifecycleTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DesignService service;
    @Autowired DesignPersistenceService persistence;
    @Autowired DesignQueryService queries;
    @Autowired DesignSnapshotService snapshots;
    @Autowired GeneratedDesignImageService images;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean AiDesignProvider provider;
    @MockitoBean StorageService storage;
    UUID project;UUID object;
    final List<UUID> projects=new ArrayList<>();
    @BeforeEach void setup() {
        reset(provider,storage);
        when(provider.getModelName()).thenReturn("test-model");
        project=seed();object=jdbc.queryForObject("SELECT object_uuid FROM detected_objects WHERE project_id=?",UUID.class,project);
    }
    @AfterEach void cleanup() { for(UUID id:projects) jdbc.update("DELETE FROM room_projects WHERE project_id=?",id); projects.clear(); }
    UUID seed() {
        UUID id=UUID.randomUUID();projects.add(id);
        jdbc.update("INSERT INTO room_projects(project_id,status,user_id) VALUES(?,'PREFERENCES_READY',?)",id,TestUserSecurity.USER_ID);
        jdbc.update("""
            INSERT INTO project_preferences(project_id,style,budget_amount,currency,room_purpose,rental_friendly)
            VALUES(?,'SCANDINAVIAN',9000,'TWD','STUDY_AND_SLEEP',TRUE)
            """,id);
        Long image=jdbc.queryForObject("""
            INSERT INTO room_images(project_id,bucket,storage_key,original_filename,mime_type,file_size_bytes,width,height)
            VALUES(?,'test','rooms/original/test.png','room.png','image/png',100,100,100) RETURNING image_id
            """,Long.class,id);
        Long internalId=jdbc.queryForObject("""
            INSERT INTO detected_objects(project_id,image_id,object_class,confidence,x_min,y_min,x_max,y_max,model_version)
            VALUES(?,?,'bed',0.9,0.1,0.1,0.5,0.5,'cv-test') RETURNING object_id
            """,Long.class,id,image);
        jdbc.update("INSERT INTO furniture_decisions(object_id,decision) VALUES(?,'KEEP')",internalId);
        return id;
    }
    String valid() { return """
        {"planVersion":"1","summary":"A practical room","designStyle":"SCANDINAVIAN",
         "actions":[{"objectId":"%s","objectClass":"bed","decision":"KEEP","type":"KEEP",
         "instruction":"Keep the existing bed in place","rationale":"Respect the saved decision"}],
         "layoutNotes":["Measurements need verification"],
         "budgetAllocation":[{"category":"storage","item":"Freestanding shelf","purpose":"More storage","estimatedCost":1000}],
         "estimatedTotalCost":1000,"currency":"TWD","warnings":[],
         "itemsNeeded":[{"objectId":null,"name":"Freestanding shelf","quantity":1}],
         "rentalFriendlyWarnings":["Confirm lease rules"],"constraints":["No drilling or permanent work"]}
        """.formatted(object); }
    StoredDesignResponse generate() {
        when(provider.generateJson(anyString(),anyString())).thenReturn(valid());return service.generate(project);
    }
    @Test void savesImmutableSnapshotMetadataAndFetchesByIdAndLatest() throws Exception {
        var result=generate();assertThat(result.status()).isEqualTo("SUCCEEDED");
        String snapshot=jdbc.queryForObject("SELECT input_snapshot::text FROM design_requests WHERE request_id=?",String.class,result.requestId());
        assertThat(mapper.readTree(snapshot).at("/objects/0/decision").asText()).isEqualTo("KEEP");
        assertThat(mapper.readTree(snapshot).at("/image/storageKey").asText()).isEqualTo("rooms/original/test.png");
        assertThat(result.promptVersion()).isEqualTo("design-v1");assertThat(result.requestVersion()).isEqualTo("1");
        jdbc.update("UPDATE project_preferences SET budget_amount=8000 WHERE project_id=?",project);
        assertThat(jdbc.queryForObject("SELECT input_snapshot::text FROM design_requests WHERE request_id=?",String.class,result.requestId())).isEqualTo(snapshot);
        mvc.perform(get("/api/projects/{id}/designs/{design}",project,result.designId())).andExpect(status().isOk()).andExpect(jsonPath("$.plan.summary").value("A practical room"));
        mvc.perform(get("/api/projects/{id}/designs/latest",project)).andExpect(status().isOk()).andExpect(jsonPath("$.designId").value(result.designId().intValue()));
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id=?",String.class,project)).isEqualTo("DESIGN_READY");
    }
    @Test void noDesignReturns204AndOtherProjectCannotReadResult() throws Exception {
        mvc.perform(get("/api/projects/{id}/designs/latest",project)).andExpect(status().isNoContent());
        var result=generate();UUID other=seed();
        mvc.perform(get("/api/projects/{id}/designs/{design}",other,result.designId())).andExpect(status().isNotFound());
    }
    @Test void failurePersistsOnlySafeCodeAndDoesNotMarkProjectReady() throws Exception {
        when(provider.generateJson(anyString(),anyString())).thenThrow(new IllegalStateException("SECRET_TOKEN https://signed.example?key=SECRET"));
        mvc.perform(post("/api/projects/{id}/designs",project)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DESIGN_GENERATION_FAILED"));
        var result=queries.latest(project);assertThat(result.status()).isEqualTo("FAILED");assertThat(result.plan()).isNull();
        assertThat(jdbc.queryForObject("SELECT row_to_json(r)::text FROM design_requests r WHERE request_id=?",String.class,result.requestId())).doesNotContain("SECRET_TOKEN","signed.example");
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id=?",String.class,project)).isEqualTo("PREFERENCES_READY");
    }
    @Test void malformedPlanRetriesOnceThenPersistsFailure() {
        when(provider.generateJson(anyString(),anyString())).thenReturn("not json");
        assertThatThrownBy(()->service.generate(project)).isInstanceOf(DesignFailureException.class);
        verify(provider,times(2)).generateJson(anyString(),anyString());assertThat(queries.latest(project).errorCode()).isEqualTo("INVALID_DESIGN_PLAN");
    }
    @Test void correctiveRetryCanSucceed() {
        when(provider.generateJson(anyString(),anyString())).thenReturn("{}",valid());
        assertThat(service.generate(project).status()).isEqualTo("SUCCEEDED");verify(provider,times(2)).generateJson(anyString(),anyString());
    }
    @Test void rejectsWrongReferenceOverspendAndIncorrectDeclaredTotal() {
        for(String invalid:List.of(valid().replace(object.toString(),UUID.randomUUID().toString()),
                valid().replace("\"estimatedTotalCost\":1000","\"estimatedTotalCost\":2000"),
                valid().replace("1000","10000"),valid().replace("\"estimatedTotalCost\":1000","\"estimatedTotalCost\":\"1000\""),
                valid().replace("\"quantity\":1","\"quantity\":1.5"),valid().replace("\"type\":\"KEEP\"","\"type\":\"REMOVE\""),
                valid().replace("\"constraints\":[\"No drilling or permanent work\"]","\"constraints\":[]"))) {
            when(provider.generateJson(anyString(),anyString())).thenReturn(invalid);
            assertThatThrownBy(()->service.generate(project)).isInstanceOf(DesignFailureException.class);
        }
    }
    @Test void rejectsChangedInputsDuringExternalCall() {
        when(provider.generateJson(anyString(),anyString())).thenAnswer(call->{jdbc.update("UPDATE project_preferences SET budget_amount=8000 WHERE project_id=?",project);return valid();});
        assertThatThrownBy(()->service.generate(project)).isInstanceOf(DesignFailureException.class);
        assertThat(queries.latest(project).errorCode()).isEqualTo("DESIGN_INPUT_CHANGED");
    }
    @Test void concurrentPendingAttemptIsRejectedAndFailureIsRetryable() {
        var first=persistence.begin(project,"openai","test-model");
        assertThatThrownBy(()->persistence.begin(project,"openai","test-model")).isInstanceOf(InvalidStateException.class);
        persistence.fail(first,"DESIGN_GENERATION_FAILED");assertThat(generate().status()).isEqualTo("SUCCEEDED");
    }
    @Test void manualStatusPatchCannotBypassValidatedDesign() throws Exception {
        mvc.perform(patch("/api/projects/{id}/status",project).contentType("application/json")
            .content("{\"status\":\"DESIGN_READY\"}"))
            .andExpect(status().isConflict());
    }
    @Test void inactiveDetectionsAreExcludedAndMissingDecisionDefaultsToUnsure() {
        jdbc.update("DELETE FROM furniture_decisions WHERE object_id IN (SELECT object_id FROM detected_objects WHERE project_id=?)",project);
        assertThat(snapshots.capture(project).objects().getFirst().decision().name()).isEqualTo("UNSURE");
        jdbc.update("UPDATE detected_objects SET active=FALSE WHERE project_id=?",project);
        assertThat(snapshots.capture(project).objects()).isEmpty();
    }
    byte[] png() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,3,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
    }
    @Test void generatedAssetUsesSharedStorageAndSurvivesLaterGet() throws Exception {
        var design=generate();byte[] bytes=png();
        when(storage.upload(anyString(),eq("image/png"),eq((long)bytes.length),any())).thenAnswer(call->new StoredObject("bucket",call.getArgument(0)));
        var image=images.store(project,design.designId(),"image/png",bytes);
        assertThat(image.status()).isEqualTo("SUCCEEDED");assertThat(image.width()).isEqualTo(2);assertThat(image.height()).isEqualTo(3);
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).upload(captor.capture(),eq("image/png"),eq((long)bytes.length),any());
        assertThat(captor.getValue()).startsWith("rooms/"+project+"/generated/"+design.designId()+"/");
        when(storage.download(captor.getValue())).thenReturn(bytes);
        mvc.perform(get(image.imageUrl())).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        assertThat(queries.get(project,design.designId()).image().imageUrl()).isEqualTo(image.imageUrl());
        images.store(project,design.designId(),"image/png",bytes);verify(storage,times(1)).upload(anyString(),anyString(),anyLong(),any());
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name='design_assets'",String.class)).doesNotContain("bytes","image_data");
    }
    @Test void storageFailureIsRecordedAndRetryCanSucceed() throws Exception {
        var design=generate();byte[] bytes=png();
        when(storage.upload(anyString(),anyString(),anyLong(),any())).thenThrow(new IllegalStateException("SECRET"));
        assertThatThrownBy(()->images.store(project,design.designId(),"image/png",bytes)).isInstanceOf(DesignFailureException.class);
        assertThat(queries.get(project,design.designId()).image().status()).isEqualTo("FAILED");
        assertThat(queries.get(project,design.designId()).image().imageUrl()).isNull();
        mvc.perform(get("/api/projects/{id}/designs/{design}/image/content",project,design.designId())).andExpect(status().isNotFound());
        doAnswer(call->new StoredObject("bucket",call.getArgument(0))).when(storage).upload(anyString(),anyString(),anyLong(),any());
        assertThat(images.store(project,design.designId(),"image/png",bytes).status()).isEqualTo("SUCCEEDED");
    }
    @Test void wrongProjectInvalidImageAndFailedPlanCannotStoreAssets() throws Exception {
        var design=generate();UUID other=seed();byte[] png=png();
        assertThatThrownBy(()->images.store(other,design.designId(),"image/png",png)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->images.store(project,design.designId(),"image/jpeg",png)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->images.store(project,design.designId(),"image/png",new byte[]{1,2})).isInstanceOf(IllegalArgumentException.class);
        var pending=persistence.begin(other,"openai","test-model");persistence.fail(pending,"DESIGN_GENERATION_FAILED");
        assertThatThrownBy(()->images.store(other,pending.designId(),"image/png",png)).isInstanceOf(InvalidStateException.class);
        verifyNoInteractions(storage);
    }
}
