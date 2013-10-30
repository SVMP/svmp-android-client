package org.mitre.svmp.performance;

import java.util.Date;

/**
 * @author Joe Portner
 * Stores information about a set of performance data
 */
public class MeasurementInfo {
    private Date startDate; // primary key
    private int connectionID; // foreign key
    private int measureInterval;
    private int pingInterval;

    public MeasurementInfo(Date startDate, int connectionID, int measureInterval, int pingInterval) {
        this.startDate = startDate;
        this.connectionID = connectionID;
        this.measureInterval = measureInterval;
        this.pingInterval = pingInterval;
    }

    public Date getStartDate() {
        return startDate;
    }

    public int getConnectionID() {
        return connectionID;
    }

    public int getMeasureInterval() {
        return measureInterval;
    }

    public int getPingInterval() {
        return pingInterval;
    }
}
