Building
========

Prerequisites:
*  Android SDK with API level 10 (GB 2.3.3)
*  Oracle JDK 6 (make sure `JAVA_HOME` is set properly)
*  ant
*  Protocol Buffers 2.5.0

Build Steps:
1.  Download the Protocol Buffers distribution and unpack it to a directory we'll call `PROTOBUF_DIR`
2.  Compile and install the protoc compiler
        cd $PROTOBUF_DIR
        ./configure
        make install
3.  Prepare the protocol buffer java runtime
        cd $PROTOBUF_DIR/java
        protoc --java_out=src/main/java -I../src ../src/google/protobuf/descriptor.proto
4.  Checkout the SVMP protocol to a directory of your choice we'll call `SVMP_PROTO`
        git clone https://github.com/SVMP/svmp-protocol-def.git $SVMP_PROTO
5.  Checkout the SVMP client code to a directory we'll call `SVMP_CLIENT`
        git clone https://github.com/SVMP/svmp-android-client.git $SVMP_CLIENT
6.  Generate the SVMP protocol source and link in the protobuf runtime
        ln -s $PROTOBUF_DIR/java/src/main/java/com $SVMP_CLIENT/src/com
        protoc -I$SVMP_PROTO --java_out=$SVMP_CLIENT/src $SVMP_PROTO/svmp.proto
7.  Prepare the client ant build (assuming the Android SDK tools are in your path)
        android update project --path $SVMP_CLIENT
8.  Build the client using ant. For example:
        cd $SVMP_CLIENT
        ant debug

    See <http://developer.android.com/tools/building/building-cmdline.html> for additional commands.

If the protocol definition file is changed, re-run the protoc command in step 6.

IDEs
====

Eclipse with the ADT:
1.  Perform steps 1-7 of Building
2.  In Eclipse, select File -> New -> Project -> Android Project from Existing Code
3.  Set the Root Directory to `$SVMP_CLIENT`.
4.  The project list should now populate. Select the checkbox next to the svmp-android-client 
    entry if it was not already. Edit the 'New Project Name' to your liking.
5.  Click Finish.

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
