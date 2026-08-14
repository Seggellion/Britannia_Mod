package com.seggellion.britannia_mod.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkillManagerCappedGainTest {
    @Test
    void gainStepCannotOvershootActivityOrDefinitionCap() {
        assertEquals(25.0F, SkillManager.nextCappedGainValue(24.95F, 100.0F, 25.0F));
        assertEquals(80.0F, SkillManager.nextCappedGainValue(79.95F, 80.0F, 100.0F));
        assertEquals(10.1F, SkillManager.nextCappedGainValue(10.0F, 100.0F, 25.0F));
    }
}
