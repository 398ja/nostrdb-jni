package xyz.tcheeric.nostrdb.inspector.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KindLabelsTest {

    @Test
    void getLabel_metadata() {
        assertEquals("Metadata", KindLabels.getLabel(0));
    }

    @Test
    void getLabel_shortTextNote() {
        assertEquals("Short Text Note", KindLabels.getLabel(1));
    }

    @Test
    void getLabel_contacts() {
        assertEquals("Contacts", KindLabels.getLabel(3));
    }

    @Test
    void getLabel_encryptedDm() {
        assertEquals("Encrypted DM", KindLabels.getLabel(4));
    }

    @Test
    void getLabel_reaction() {
        assertEquals("Reaction", KindLabels.getLabel(7));
    }

    @Test
    void getLabel_zap() {
        assertEquals("Zap", KindLabels.getLabel(9735));
    }

    @Test
    void getLabel_zapRequest() {
        assertEquals("Zap Request", KindLabels.getLabel(9734));
    }

    @Test
    void getLabel_relayList() {
        assertEquals("Relay List", KindLabels.getLabel(10002));
    }

    @Test
    void getLabel_longFormContent() {
        assertEquals("Long-form Content", KindLabels.getLabel(30023));
    }

    @Test
    void getLabel_applicationSpecificData() {
        assertEquals("Application-specific Data", KindLabels.getLabel(30078));
    }

    @Test
    void getLabel_unknownKind() {
        assertEquals("Kind 99999", KindLabels.getLabel(99999));
    }

    @Test
    void getLabel_unknownNegativeKind() {
        assertEquals("Kind -1", KindLabels.getLabel(-1));
    }

    @Test
    void getLabel_allDefinedKinds() {
        // Verify all 34 defined kinds return non-default labels
        int[] definedKinds = {0, 1, 2, 3, 4, 5, 6, 7, 8, 16, 40, 41, 42, 43, 44,
                1063, 1984, 9734, 9735, 10000, 10001, 10002, 13194,
                22242, 23194, 23195, 24133, 27235, 30000, 30001,
                30008, 30009, 30023, 30078};

        for (int kind : definedKinds) {
            String label = KindLabels.getLabel(kind);
            assertFalse(label.startsWith("Kind "),
                    "Kind " + kind + " should have a defined label but got: " + label);
        }
    }
}
