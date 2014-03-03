/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp.performance;

/**
 * @author Joe Portner
 * This object keeps track of performance measurements that are taken over a span of time
 */
public class SpanPerformanceData {
    private int frameCount;
    private int sensorUpdates;
    private int touchUpdates;

    public SpanPerformanceData() {}

    public synchronized SpanPerformanceData reset() {
        // create a copy of the measurements taken
        SpanPerformanceData copy = new SpanPerformanceData();
        copy.frameCount = frameCount;
        copy.sensorUpdates = sensorUpdates;
        copy.touchUpdates = touchUpdates;

        // reset measurements
        frameCount = 0;
        sensorUpdates = 0;
        touchUpdates = 0;

        return copy;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getSensorUpdates() {
        return sensorUpdates;
    }

    public int getTouchUpdates() {
        return touchUpdates;
    }

    public synchronized void incrementFrameCount() {
        this.frameCount++;
    }
    public synchronized void incrementSensorUpdates() {
        this.sensorUpdates++;
    }
    public synchronized void incrementTouchUpdates() {
        this.touchUpdates++;
    }

    public String toString() {
        return String.format("frameCount '%d', sensorUpdates '%d', touchUpdates '%d'",
                frameCount, sensorUpdates, touchUpdates);
    }
}
