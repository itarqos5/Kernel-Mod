package dev.kernel.fabric.shader.pack;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShaderIdentifierRulesTest {
    private static ShaderIdentifierRules blocks(String source) {
        return ShaderIdentifierRules.parse(source, ShaderIdentifierRules.BLOCK);
    }

    @Test void anEntryWithoutANamespaceIsAVanillaOne() {
        var rules = blocks("block.1=stone minecraft:granite");
        assertEquals(2, rules.rules().size());
        assertEquals("minecraft", rules.rules().get(0).namespace());
        assertEquals("stone", rules.rules().get(0).path());
        assertEquals("minecraft", rules.rules().get(1).namespace());
        assertEquals("granite", rules.rules().get(1).path());
        assertTrue(rules.rules().get(0).everyState());
    }

    @Test void anEntryKeepsItsOwnNamespace() {
        var rule = blocks("block.7=somemod:machine").rules().get(0);
        assertEquals("somemod", rule.namespace());
        assertEquals("machine", rule.path());
        assertEquals(7, rule.value());
    }

    @Test void stateConstraintsConfineARuleToTheStatesTheyName() {
        var rule = blocks("block.2=minecraft:grass_block:snowy=true").rules().get(0);
        assertFalse(rule.everyState());
        assertTrue(rule.matches(Map.of("snowy", "true")));
        assertFalse(rule.matches(Map.of("snowy", "false")));
        assertFalse(rule.matches(Map.of()), "a state the rule names must be present to match");

        var either = blocks("block.3=minecraft:leaves:persistent=true,false").rules().get(0);
        assertTrue(either.matches(Map.of("persistent", "true")));
        assertTrue(either.matches(Map.of("persistent", "false")));
    }

    @Test void severalConstraintsAllHaveToHold() {
        var rule = blocks("block.4=minecraft:door:half=upper:open=false").rules().get(0);
        assertTrue(rule.matches(Map.of("half", "upper", "open", "false")));
        assertFalse(rule.matches(Map.of("half", "upper", "open", "true")));
        assertFalse(rule.matches(Map.of("half", "lower", "open", "false")));
    }

    @Test void anUnconstrainedRuleMatchesEveryState() {
        var rule = blocks("block.5=minecraft:stone").rules().get(0);
        assertTrue(rule.matches(Map.of()));
        assertTrue(rule.matches(Map.of("anything", "at-all")));
    }

    @Test void onlyTheAskedForPrefixIsRead() {
        String source = "block.1=stone\nentity.2=minecraft:creeper\nitem.3=minecraft:torch\n";
        assertEquals(1, blocks(source).rules().size());
        assertEquals("creeper", ShaderIdentifierRules.parse(source, ShaderIdentifierRules.ENTITY).rules().get(0).path());
        assertEquals("torch", ShaderIdentifierRules.parse(source, ShaderIdentifierRules.ITEM).rules().get(0).path());
    }

    @Test void oneValueMayBeClaimedByManyEntriesAndLines() {
        var rules = blocks("block.9=minecraft:stone minecraft:granite\nblock.9=minecraft:diorite\n");
        assertEquals(3, rules.rules().size());
        for (var rule : rules.rules()) assertEquals(9, rule.value());
    }

    @Test void commentsBlankLinesAndForeignKeysAreSkipped() {
        var rules = blocks("# a comment\n\n// another\nsomething.else=1\nblock.1=stone\n");
        assertEquals(1, rules.rules().size());
    }

    @Test void unreadableLinesAreSkippedRatherThanFailingTheWholeFile() {
        // A pack with one bad entry still maps everything else it declares.
        var rules = blocks("block.1=stone\nblock.2=:::\nblock.x=granite\nblock.3=minecraft:dirt:broken\nblock.4=diorite\n");
        assertEquals(2, rules.rules().size());
        assertEquals(1, rules.rules().get(0).value());
        assertEquals(4, rules.rules().get(1).value());
    }

    @Test void identitiesOutsideTheRangeAPackMayClaimAreSkipped() {
        assertTrue(blocks("block.65536=stone").isEmpty());
        assertTrue(blocks("block.999999=stone").isEmpty());
        assertEquals(65535, blocks("block.65535=stone").rules().get(0).value());
    }

    @Test void anEntryNamingSomethingThisGameLacksIsStillKept() {
        // Packs list blocks from mods that are not installed; resolution decides, not parsing.
        assertEquals("somemod", blocks("block.1=somemod:absent_block").rules().get(0).namespace());
    }

    @Test void theRuleCountIsBounded() {
        var source = new StringBuilder("block.1=");
        for (int entry = 0; entry < ShaderIdentifierRules.MAX_RULES + 64; entry++) source.append("stone").append(entry).append(' ');
        assertEquals(ShaderIdentifierRules.MAX_RULES, blocks(source.toString()).rules().size());
    }

    @Test void aMissingFileIsAnEmptyMap() {
        assertTrue(ShaderIdentifierRules.parse(null, ShaderIdentifierRules.BLOCK).isEmpty());
        assertTrue(ShaderIdentifierRules.parse("", ShaderIdentifierRules.BLOCK).isEmpty());
    }
}
