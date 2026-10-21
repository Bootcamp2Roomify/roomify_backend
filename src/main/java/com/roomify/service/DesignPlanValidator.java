package com.roomify.service;

import com.roomify.dto.design.*;
import com.roomify.entity.FurnitureDecisionType;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.roomify.dto.design.LayoutChange.Type.*;

@Component
public class DesignPlanValidator {
    private final Validator validator;
    public DesignPlanValidator(Validator validator) { this.validator=validator; }
    public void validate(DesignPlan plan, DesignRequestSnapshot snapshot) {
        if(plan==null || !validator.validate(plan).isEmpty()) invalid();
        if(!plan.currency().equals(snapshot.budget().currency())) invalid();
        BigDecimal sum=plan.budgetAllocation().stream().map(BudgetAllocation::estimatedCost)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        if(sum.compareTo(plan.estimatedTotalCost())!=0 || sum.compareTo(snapshot.budget().amount())>0) invalid();
        var expected=new HashMap<UUID,DesignRequestSnapshot.Furniture>();
        snapshot.objects().forEach(o->expected.put(o.objectId(),o));
        var seen=new HashSet<UUID>();
        for(var a:plan.actions()) {
            if(a.type()==ADD) { if(a.objectId()!=null || a.decision()!=null) invalid(); continue; }
            var original=expected.get(a.objectId());
            if(original==null || !seen.add(a.objectId()) || a.decision()!=original.decision()
                    || !a.objectClass().equals(original.label())) invalid();
            boolean matches=switch(original.decision()) {
                case KEEP -> a.type()==KEEP || a.type()==KEEP_AND_REPOSITION;
                case REMOVE -> a.type()==REMOVE;
                case REPLACE -> a.type()==REPLACE;
                case UNSURE -> a.type()==UNSURE;
            };
            if(!matches) invalid();
        }
        if(!seen.equals(expected.keySet())) invalid();
        for(var item:plan.itemsNeeded()) if(item.objectId()!=null && !expected.containsKey(item.objectId())) invalid();
        if(snapshot.preferences().rentalFriendly() && plan.constraints().isEmpty()) invalid();
    }
    private void invalid() { throw new IllegalArgumentException("INVALID_DESIGN_PLAN"); }
}
