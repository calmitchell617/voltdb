/* This file is part of VoltDB.
 * Copyright (C) 2008-2020 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package org.voltdb;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;
import org.voltdb.StatsProcOutputTable.ProcOutputRow;

public class TestStatsProcOutputTable {

    private static final long mB = 1024 * 1024;

    // result row in java form for test
    static class ResultRow {
        long timestamp;
        String procedure;
        long percent;
        long invocations;
        long minOUT;
        long maxOUT;
        long avgOUT;
        long totalOUT;

        public ResultRow(String procedure, long timestamp, long percent, long invocations,
                         long minOUT, long maxOUT, long avgOUT, long totalOUT) {
            this.timestamp = timestamp;
            this.procedure = procedure;
            this.percent = percent;
            this.invocations = invocations;
            this.minOUT = minOUT;
            this.maxOUT = maxOUT;
            this.avgOUT = avgOUT;
            this.totalOUT = totalOUT;
        }
    }

    // push rows from data in to the table.
    void loadData(StatsProcOutputTable dut, ProcOutputRow[] data) {
        for (ProcOutputRow row : data) {
            dut.updateTable(true,
                            row.procedure,
                            row.partition,
                            row.timestamp,
                            row.invocations,
                            row.minOUT,
                            row.maxOUT,
                            row.avgOUT);
        }
    }

    // push rows from data in to the table.
    void loadEmUpNoDeDup(StatsProcOutputTable dut, ProcOutputRow[] data) {
        for (ProcOutputRow row : data) {
            dut.updateTable(false,
                            row.procedure,
                            row.partition,
                            row.timestamp,
                            row.invocations,
                            row.minOUT,
                            row.maxOUT,
                            row.avgOUT);
        }
    }

    // validate contents of sorted dut vs. expectation of ResultRow[]
    void assertEquals(String testname, StatsProcOutputTable dut, ResultRow[] data) {
        VoltTable vt = dut.sortByOutput(testname);
        Assert.assertEquals(
                testname + " has wrong number of result rows in test.",
                vt.getRowCount(),
                data.length
        );

        for (ResultRow resultRow : data) {
            assertTrue(vt.advanceRow());
            System.out.printf("%s: validating row %d\n", testname, vt.getActiveRowIndex());

            Assert.assertEquals(resultRow.percent, vt.getLong("WEIGHTED_PERC"));
            Assert.assertEquals(resultRow.timestamp, vt.getLong("TIMESTAMP"));
            Assert.assertEquals(resultRow.procedure, vt.getString("PROCEDURE"));
            Assert.assertEquals(resultRow.invocations, vt.getLong("INVOCATIONS"));
            Assert.assertEquals(resultRow.minOUT, vt.getLong("MIN_RESULT_SIZE"));
            Assert.assertEquals(resultRow.maxOUT, vt.getLong("MAX_RESULT_SIZE"));
            Assert.assertEquals(resultRow.avgOUT, vt.getLong("AVG_RESULT_SIZE"));
            Assert.assertEquals(resultRow.totalOUT, vt.getLong("TOTAL_RESULT_SIZE_MB"));
        }
    }

    @Test
    public void testBaseCase() {
        // Given
        // validate sensical round-trip of one row.
        ProcOutputRow[] data = {
                //proc/part/time/invok/min/max/avg
                new ProcOutputRow("proc", 0L, 12345L, 100 * mB, 2L, 4L, 3L)
        };

        ResultRow[] result = {
                //time/proc/perc/inok/min/max/avg/tot
                new ResultRow("proc", 12345L, 100L, 100 * mB, 2L, 4L, 3L, 300L)
        };

        // When
        StatsProcOutputTable dut = new StatsProcOutputTable();
        loadData(dut, data);

        // Then
        assertEquals("testBaseCase", dut, result);
    }

    @Test
    public void testAllZeros() {
        // Given
        // validate paranoia about an all zero row - just in case.
        ProcOutputRow[] data = {     //proc/part/time/invok/min/max/avg
                new ProcOutputRow("proc", 0L, 0L, 0L, 0L, 0L, 0L)

        };

        ResultRow[] result = {  //time/proc/perc/inok/min/max/avg/tot
                new ResultRow("proc", 0L, 100L, 0L, 0L, 0L, 0L, 0L)
        };

        // When
        StatsProcOutputTable dut = new StatsProcOutputTable();
        loadData(dut, data);

        // Then
        assertEquals("testAllZeros", dut, result);
    }

    @Test
    public void testMultipleProcs() {
        // Given
        ProcOutputRow[] data = {     //proc/part/time/invok/min/max/avg
                new ProcOutputRow("A", 0L, 12345L, 300 * mB, 3L, 5L, 4L),
                new ProcOutputRow("B", 0L, 12345L, 100 * mB, 1L, 4L, 2L),
                new ProcOutputRow("B", 1L, 12345L, 100 * mB, 1L, 3L, 2L)
        };

        ResultRow[] result = {  //time/proc/perc/inok/min/max/avg/tot
                new ResultRow("A", 12345L, 75L, 300 * mB, 3L, 5L, 4L, 1200L),
                new ResultRow("B", 12345L, 25L, 200 * mB, 1L, 4L, 2L, 400L)
        };

        // When
        StatsProcOutputTable dut = new StatsProcOutputTable();
        loadData(dut, data);

        // Then
        assertEquals("testMulipleProcs", dut, result);
    }

    @Test
    public void testSiteDedupe() {
        // Given
        // need to not double count invocations at replicas, but do look at
        // min, max, avg
        ProcOutputRow[] data = { //proc/part/time/invok/min/max/avg
                new ProcOutputRow("proc", 0L, 12345L, 200 * mB, 4L, 10L, 6L),
                new ProcOutputRow("proc", 0L, 12345L, 100 * mB, 4L, 25L, 10L),
                new ProcOutputRow("proc", 0L, 12345L, 100 * mB, 1L, 4L, 2L),
                new ProcOutputRow("proc", 1L, 12345L, 400 * mB, 2L, 8L, 4L)
        };

        ResultRow[] result = { //time/proc/perc/inok/min/max/avg/tot
                new ResultRow("proc", 12345L, 100L, 800 * mB, 1L, 25L, 4L /* this should be 5! */,
                              3200L /* this should be 4000 */)
        };

        // When
        StatsProcOutputTable dut = new StatsProcOutputTable();
        loadEmUpNoDeDup(dut, data);

        // Then
        assertEquals("testSiteDedupe", dut, result);
    }

    @Test
    public void testRounding() {
        // Given
        ProcOutputRow[] data = {     //proc/part/time/invok/min/max/avg
                new ProcOutputRow("A", 0L, 12345L, 10000000 * mB, 3L, 5L, 4L),
                new ProcOutputRow("B", 0L, 12345L, 1 * mB, 1L, 4L, 2L)
        };

        ResultRow[] result = {  //time/proc/perc/inok/min/max/avg/tot
                new ResultRow("A", 12345L, 100L, 10000000 * mB, 3L, 5L, 4L, 40000000L),
                new ResultRow("B", 12345L, 0L, 1 * mB, 1L, 4L, 2L, 2L)
        };

        // When
        StatsProcOutputTable dut = new StatsProcOutputTable();
        loadData(dut, data);

        // Then
        assertEquals("testRounding", dut, result);
    }
}
