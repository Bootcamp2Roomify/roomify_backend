-- Marks concept images served from the bundled demo library (AI_DEMO_MODE or fallback) apart from generated ones.
ALTER TABLE design_assets ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'ai' CHECK (source IN ('ai','demo'));
