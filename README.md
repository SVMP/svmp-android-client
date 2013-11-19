Building
========

Prerequisites:

*  Up-to-date Android SDK with at least API level 17, with ANDROID_HOME environment variable set correctly
*  Oracle JDK 6 (make sure `JAVA_HOME` is set properly)
*  ant

Build Steps:

1. Check out the SVMP client and protocol to a directory of your choice
        cd ${SVMP}
        git clone https://github.com/SVMP/svmp-protocol-def.git -b svmp-1.1
        git clone https://github.com/SVMP/svmp-android-client.git -b svmp-1.1
2.  Build the client using ant. For example:
        cd ${SVMP}/svmp-android-client
        ant debug

    See <http://developer.android.com/tools/building/building-cmdline.html> for additional commands.

IDEs
====

Eclipse with the ADT:

1.  Check out the code as above
2.  In Eclipse, select File -> New -> Project -> Android Project from Existing Code
3.  Set the Root Directory to where the client code is checked out (`${SVMP}/svmp-android-client`).
4.  The project list should now populate. Select the checkbox next to the svmp-android-client 
    entry if it was not already. Edit the 'New Project Name' to your liking.
5.  Click Finish.
6.  Add ${SVMP}/svmp-protocol-def/protobuf-2.5.0/protobuf-java-2.5.0.jar to the project as an external library
7.  Add ${SVMP}/svmp-protocol-def/src as an addtional source path to the project
8.  Under Build Path -> Order and Export, check the box next to the protobuf-java-2.5.0.jar entry

License
=======
Copyright (c) 2012-2013, The MITRE Corporation, All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
