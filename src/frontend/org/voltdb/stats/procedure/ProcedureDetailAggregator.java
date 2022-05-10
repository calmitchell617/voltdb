/* This file is part of VoltDB.
 * Copyright (C) 2022 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.voltdb.stats.procedure;

import java.util.Map;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google_voltpatches.common.collect.ImmutableMap;
import org.voltdb.CatalogContext;
import org.voltdb.StatsProcInputTable;
import org.voltdb.StatsProcOutputTable;
import org.voltdb.StatsProcProfTable;
import org.voltdb.VoltDB;
import org.voltdb.VoltSystemProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.catalog.Procedure;

public class ProcedureDetailAggregator {

    // We need to use Supplier to break circular dependency during initialziation of VoltDB.
    private final Supplier<Map<String, Boolean>> m_procedureInfo;

    public static ProcedureDetailAggregator create() {
        return new ProcedureDetailAggregator(getProcedureInformation());
    }

    public ProcedureDetailAggregator(Supplier<Map<String, Boolean>> m_procedureInfo) {
        this.m_procedureInfo = m_procedureInfo;
    }

    public VoltTable[] sortProcedureDetailStats(VoltTable[] baseStats) {
        ProcedureDetailResultTable result = new ProcedureDetailResultTable(baseStats[0]);
        return result.getSortedResultTable();
    }

    /**
     * Produce PROCEDURE aggregation of PROCEDURE subselector
     * Basically it leaves out the rows that were not labeled as "<ALL>".
     */
    public VoltTable[] aggregateProcedureStats(VoltTable[] baseStats) {
        VoltTable result = new VoltTable(
                new VoltTable.ColumnInfo("TIMESTAMP", VoltType.BIGINT),
                new VoltTable.ColumnInfo(VoltSystemProcedure.CNAME_HOST_ID, VoltSystemProcedure.CTYPE_ID),
                new VoltTable.ColumnInfo("HOSTNAME", VoltType.STRING),
                new VoltTable.ColumnInfo(VoltSystemProcedure.CNAME_SITE_ID, VoltSystemProcedure.CTYPE_ID),
                new VoltTable.ColumnInfo("PARTITION_ID", VoltType.INTEGER),
                new VoltTable.ColumnInfo("PROCEDURE", VoltType.STRING),
                new VoltTable.ColumnInfo("INVOCATIONS", VoltType.BIGINT),
                new VoltTable.ColumnInfo("TIMED_INVOCATIONS", VoltType.BIGINT),
                new VoltTable.ColumnInfo("MIN_EXECUTION_TIME", VoltType.BIGINT),
                new VoltTable.ColumnInfo("MAX_EXECUTION_TIME", VoltType.BIGINT),
                new VoltTable.ColumnInfo("AVG_EXECUTION_TIME", VoltType.BIGINT),
                new VoltTable.ColumnInfo("MIN_RESULT_SIZE", VoltType.INTEGER),
                new VoltTable.ColumnInfo("MAX_RESULT_SIZE", VoltType.INTEGER),
                new VoltTable.ColumnInfo("AVG_RESULT_SIZE", VoltType.INTEGER),
                new VoltTable.ColumnInfo("MIN_PARAMETER_SET_SIZE", VoltType.INTEGER),
                new VoltTable.ColumnInfo("MAX_PARAMETER_SET_SIZE", VoltType.INTEGER),
                new VoltTable.ColumnInfo("AVG_PARAMETER_SET_SIZE", VoltType.INTEGER),
                new VoltTable.ColumnInfo("ABORTS", VoltType.BIGINT),
                new VoltTable.ColumnInfo("FAILURES", VoltType.BIGINT),
                new VoltTable.ColumnInfo("TRANSACTIONAL", VoltType.TINYINT));
        baseStats[0].resetRowPosition();
        while (baseStats[0].advanceRow()) {
            if (baseStats[0].getString("STATEMENT").equalsIgnoreCase("<ALL>")) {
                result.addRow(
                        baseStats[0].getLong("TIMESTAMP"),
                        baseStats[0].getLong(VoltSystemProcedure.CNAME_HOST_ID),
                        baseStats[0].getString("HOSTNAME"),
                        baseStats[0].getLong(VoltSystemProcedure.CNAME_SITE_ID),
                        baseStats[0].getLong("PARTITION_ID"),
                        baseStats[0].getString("PROCEDURE"),
                        baseStats[0].getLong("INVOCATIONS"),
                        baseStats[0].getLong("TIMED_INVOCATIONS"),
                        baseStats[0].getLong("MIN_EXECUTION_TIME"),
                        baseStats[0].getLong("MAX_EXECUTION_TIME"),
                        baseStats[0].getLong("AVG_EXECUTION_TIME"),
                        baseStats[0].getLong("MIN_RESULT_SIZE"),
                        baseStats[0].getLong("MAX_RESULT_SIZE"),
                        baseStats[0].getLong("AVG_RESULT_SIZE"),
                        baseStats[0].getLong("MIN_PARAMETER_SET_SIZE"),
                        baseStats[0].getLong("MAX_PARAMETER_SET_SIZE"),
                        baseStats[0].getLong("AVG_PARAMETER_SET_SIZE"),
                        baseStats[0].getLong("ABORTS"),
                        baseStats[0].getLong("FAILURES"),
                        (byte) baseStats[0].getLong("TRANSACTIONAL"));
            }
        }
        return new VoltTable[]{result};
    }

    /**
     * Produce PROCEDUREPROFILE aggregation of PROCEDURE subselector
     */
    public VoltTable[] aggregateProcedureProfileStats(VoltTable[] baseStats) {
        StatsProcProfTable timeTable = new StatsProcProfTable();
        baseStats[0].resetRowPosition();
        while (baseStats[0].advanceRow()) {
            // Skip non-transactional procedures for some of these rollups until
            // we figure out how to make them less confusing.
            // NB: They still show up in the raw PROCEDURE stata.
            boolean transactional = baseStats[0].getLong("TRANSACTIONAL") == 1;
            if (!transactional) {
                continue;
            }

            if (!baseStats[0].getString("STATEMENT").equalsIgnoreCase("<ALL>")) {
                continue;
            }
            String pname = baseStats[0].getString("PROCEDURE");

            timeTable.updateTable(!isReadOnlyProcedure(pname),
                                  baseStats[0].getLong("TIMESTAMP"),
                                  pname,
                                  baseStats[0].getLong("PARTITION_ID"),
                                  baseStats[0].getLong("INVOCATIONS"),
                                  baseStats[0].getLong("MIN_EXECUTION_TIME"),
                                  baseStats[0].getLong("MAX_EXECUTION_TIME"),
                                  baseStats[0].getLong("AVG_EXECUTION_TIME"),
                                  baseStats[0].getLong("FAILURES"),
                                  baseStats[0].getLong("ABORTS"));
        }
        return new VoltTable[]{timeTable.sortByAverage("EXECUTION_TIME")};
    }

    /**
     * Produce PROCEDUREINPUT aggregation of PROCEDURE subselector
     */
    public VoltTable[] aggregateProcedureInputStats(VoltTable[] baseStats) {
        StatsProcInputTable timeTable = new StatsProcInputTable();
        baseStats[0].resetRowPosition();
        while (baseStats[0].advanceRow()) {
            // Skip non-transactional procedures for some of these rollups until
            // we figure out how to make them less confusing.
            // NB: They still show up in the raw PROCEDURE stata.
            boolean transactional = baseStats[0].getLong("TRANSACTIONAL") == 1;
            if (!transactional) {
                continue;
            }

            if (!baseStats[0].getString("STATEMENT").equalsIgnoreCase("<ALL>")) {
                continue;
            }
            String pname = baseStats[0].getString("PROCEDURE");
            timeTable.updateTable(!isReadOnlyProcedure(pname),
                                  pname,
                                  baseStats[0].getLong("PARTITION_ID"),
                                  baseStats[0].getLong("TIMESTAMP"),
                                  baseStats[0].getLong("INVOCATIONS"),
                                  baseStats[0].getLong("MIN_PARAMETER_SET_SIZE"),
                                  baseStats[0].getLong("MAX_PARAMETER_SET_SIZE"),
                                  baseStats[0].getLong("AVG_PARAMETER_SET_SIZE")
            );
        }
        return new VoltTable[]{timeTable.sortByInput("PROCEDURE_INPUT")};
    }

    /**
     * Produce PROCEDUREOUTPUT aggregation of PROCEDURE subselector
     */
    public VoltTable[] aggregateProcedureOutputStats(VoltTable[] baseStats) {
        StatsProcOutputTable timeTable = new StatsProcOutputTable();
        baseStats[0].resetRowPosition();
        while (baseStats[0].advanceRow()) {
            // Skip non-transactional procedures for some of these rollups until
            // we figure out how to make them less confusing.
            // NB: They still show up in the raw PROCEDURE stata.
            boolean transactional = baseStats[0].getLong("TRANSACTIONAL") == 1;
            if (!transactional) {
                continue;
            }

            if (!baseStats[0].getString("STATEMENT").equalsIgnoreCase("<ALL>")) {
                continue;
            }
            String pname = baseStats[0].getString("PROCEDURE");
            timeTable.updateTable(!isReadOnlyProcedure(pname),
                                  pname,
                                  baseStats[0].getLong("PARTITION_ID"),
                                  baseStats[0].getLong("TIMESTAMP"),
                                  baseStats[0].getLong("INVOCATIONS"),
                                  baseStats[0].getLong("MIN_RESULT_SIZE"),
                                  baseStats[0].getLong("MAX_RESULT_SIZE"),
                                  baseStats[0].getLong("AVG_RESULT_SIZE")
            );
        }

        return new VoltTable[]{timeTable.sortByOutput("PROCEDURE_OUTPUT")};
    }

    private static Supplier<Map<String, Boolean>> getProcedureInformation() {
        return Suppliers.memoize(() -> {
            CatalogContext ctx = VoltDB.instance().getCatalogContext();

            ImmutableMap.Builder<String, Boolean> builder = ImmutableMap.builder();
            for (Procedure p : ctx.procedures) {
                builder.put(p.getClassname(), p.getReadonly());
            }

            return builder.build();
        });
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isReadOnlyProcedure(String name) {
        return m_procedureInfo.get().getOrDefault(name, false);
    }
}
