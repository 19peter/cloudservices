package org.cloudServices.cmd;

import org.cloudServices.global.db.Memdb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDispatcherTest {

    @BeforeAll
    static void initializeMemdb() {
        Memdb.init();
    }

    @Test
    void createHelpListsCapacityAndNameOptions() {
        String response = CommandDispatcher.dispatch("cld cu create --help");

        assertTrue(response.contains("--capacity"));
        assertTrue(response.contains("--name"));
    }

    @Test
    void invalidCapacityReturnsPicocliUsage() {
        String response = CommandDispatcher.dispatch("cld cu create --capacity \"not valid\"");

        assertTrue(response.contains("capacity"));
        assertTrue(response.contains("not valid"));
        assertTrue(response.contains("Usage:"));
    }

    @Test
    void createUsesBuilderDefaultWhenCapacityIsOmitted() {
        String response = CommandDispatcher.dispatch("cld cu create");

        assertTrue(response.contains("ComputeUnit created successfully"));
    }

    @Test
    void createAcceptsNamedCapacityAndQuotedName() {
        String response = CommandDispatcher.dispatch(
                "cld cu create --name \"unit with spaces\" --capacity LARGE");

        assertTrue(response.contains("[Name: unit with spaces]"));
    }

    @Test
    void listShowsNameAndDoesNotDuplicateNamedUnits() {
        String unitName = "list-name-test";
        CommandDispatcher.dispatch("cld cu create --name " + unitName);

        String response = CommandDispatcher.dispatch("cld cu list");
        long matchingRows = response.lines()
                .filter(line -> line.contains("Name: " + unitName))
                .count();

        assertEquals(1, matchingRows);
    }
}
