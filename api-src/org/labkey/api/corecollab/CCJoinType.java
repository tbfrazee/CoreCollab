package org.labkey.api.corecollab;

/**Establishes how the CCAutoJoinTable joins tables - either by date (table.date = joinedTable.date) or by most recent date (table.date >= joinTable.date)
 PREV incurs additional cost by adding a subquery to each row to find the proper date to match.
 */
public enum CCJoinType
{
    DATE,
    PREV,
    CUSTOM //Currently not used
}
