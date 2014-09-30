# SVMP Android Client

## Setup

### Prerequisites

*  Up-to-date Android SDK with at least API level 17, with `ANDROID_HOME` environment variable set correctly
*  Oracle JDK 6, with `JAVA_HOME` environment variable set correctly
*  ant

### Build Steps

1. Check out the SVMP client and protocol to a directory of your choice

 ```sh
 $ cd ${SVMP}
 $ git clone https://github.com/SVMP/svmp-protocol-def.git
 $ git clone https://github.com/SVMP/svmp-android-client.git
 ```

2. Build the client using ant. For example:

 ```sh
 $ cd ${SVMP}/svmp-android-client
 $ ant release
 ```
 See [Building and Running from the Command Line](http://developer.android.com/tools/building/building-cmdline.html) for additional commands.

### Configuration

#### Server Trust
There are three ways to configure server trust in the client.

1. **Pinned Trust Store**

 If `res/raw/client_truststore.bks` is not empty, the client will use it as the pinned trust store.
 This means that *only* certificates that validate through that trust store will be accepted.

 By default, the file is empty. If you want to use this option you must place the certs to trust in
 the server_certs/ directory before compiling the client. The build process will import all files
 with the .pem extension from the `server_certs/` directory.

 If you encounter errors related to the keystore password, ensure the value for *TRUSTSTORE_PASSWORD*
 in the file `src/org/mitre/svmp/common/Constants.java` matches the *client.truststore.password*
 property in `custom_rules.xml`.
 
 The build process will attempt to download the Bouncy Castle crypto provider jar needed for
 creating the keystore. If this fails, check that your http proxy settings for ant are correct.

2. **Certificate Dialog**

 If the Pinned Trust store is not used, and the *"Connection"* -> *"Show certificate dialog"* preference is enabled, the user will be given a prompt when an unknown certificate is encountered. The prompt options allow the user to *Abort*, accept *Once*, or accept *Always*.

 If you want to use this option you must enable the aforementioned preference. By default, the preference is disabled.

3. **Default Trust Store**

 If the Pinned Trust Store and the Certificate Dialog are not used, the client will use the system trust store. This consists of normal trusted Android CA certs. This is the default server trust option.

#### Performance Instrumentation

If the *"Performance"* -> *"Take measurements"* preference is enabled, the client will record performance instrumentation measurements. These are stored in the client's SQLite database. By default, measurements are taken every 1 second. Measured values include: `FrameCount`, `SensorUpdates`, `TouchUpdates`, `CPUUsage`, `MemoryUsage`, `WifiStrength`, `BatteryLevel`, `CellNetwork`, `CellValues`, and `Ping`.

Measurement records are separated every time the client connects to a server. In Preferences, you can export measurement data to CSV files or wipe existing performance data from the database.

#### Sensor Data

In the *"Sensors"* preferences, you can adjust what sensors are polled to send data to the server. You can also adjust how often sensor data is sent. Note: sending too much sensor data can result in poor performance. We recommend leaving these values set to their defaults.

## IDEs

First, check out the code as above.
If you plan on using self-signed certificates for your SVMP servers, you should create a Pinned Trust Store to compile into the client.
To do this, generate a Bouncy Castle trust store:

1. Check out the code as above.
2. Navigate into the code directory.
3. Copy any *.pem* certificate files (for your CA, or preferably for individual servers) into the `server_certs/` directory.
4. Run the following command to generate the trust store:

 ```sh
 $ ant import_client_truststore_certs
 ```

Now, Proceed to build the client with the IDE of your choice.

### Eclipse with the ADT:

1. In Eclipse, select *File* -> *New* -> *Project* -> *Android Project from Existing Code*
2. Set the Root Directory to where the client code is checked out (`${SVMP}/svmp-android-client`).
3. The project list should now populate. Ensure the *svmp-android-client* and *MemorizingTrustManager* entries are checked. Edit the 'New Project Name' to your liking.
4. Click *Finish*.
5. Add `${SVMP}/svmp-protocol-def/protobuf-2.5.0/protobuf-java-2.5.0.jar` to the project as an external library
6. Add `${SVMP}/svmp-protocol-def/src` as an addtional source path to the project
7. Under *Build Path* -> *Order and Export*, check the box next to the *protobuf-java-2.5.0.jar* entry

### IntelliJ IDEA:

1. In IDEA, select *File* -> *Import Project* (or, from the Welcome Screen, select *Import Project*)
2. Set the Root Directory to where the client code is checked out (`${SVMP}/svmp-android-client`)
3. Select *Create project from existing sources* -> *Next*
4. Edit the *Project name* to your liking, then select *Next*
5. Ensure that the project directory and src directories are checked, then select *Next*
6. Select *Next* at the prompts for the libraries and module structure
7. Select an Android platform for your SDK, then select *Next* (or, if you don't have one listed, select *+* to add a new SDK)
8. Ensure that both *AndroidManifest.xml* files are checked, then select *Finish*
9. Select *File* -> *Project Structure...*, then select *Modules* -> *svmp-protocol-def*, then select the *Dependencies* tab, then select *+* -> *Library...* -> *protobuf-java-2.5.0* -> *Add Selected*
10. Select *Modules* -> *svmp-android-client*, then select the *Dependencies* tab, then select *+* -> *Library...* -> *protobuf-java-2.5.0* -> *Add Selected*, then select *OK*
11. Select *Run* -> *Edit Configurations...*, under *Target Device* select *Show chooser dialog* to allow for target device selection, then select *OK*

## License

Copyright (c) 2012-2014, The MITRE Corporation, All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
