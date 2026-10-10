package holyflame.administration;

import holyflame.administration.model.Note;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteTypeNormalizationTest {

    @Test
    void reconnaitLePremierDevoirAvecUnEspace() {
        assertEquals(Note.TYPE_DEVOIR, Note.normalizeType("DEVOIR 1"));
        assertEquals(Note.TYPE_DEVOIR, Note.normalizeType(" devoir 1 "));
    }
}