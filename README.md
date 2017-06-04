Project init 6
============

From scratch implementation of a battle.net like server for channel wars aka "warnet". Written in Scala.<br/>

It's built on the premise that first person to enter a private channel gets operator status like old battle.net.<br/>

It also has the capability to track statistics and extract ranking data out of them.

* It neither supports nor is meant to support real battle.net game clients. 
* It should support most bots.
* Supports both binary and telnet protocols.
  * Including a telnet/IRC-like "init 6" protocol. Documentation for which is included in the repo as a pdf
  * Includes **websocket** support for init 6 protocol. Great for webbots!
* Is meant to drop at set intervals.
* Supports cluster setup. Servers sync with each other, no need for a hub.

Setup
============
All of the included scripts are meant to be run on a Linux system, though considering it's a JVM-based project, there should be only little effort necessary to get it working under windows/mac.<br/>
The existing setup scripts were first written for centos6 but I made some changes for centos7 for last server deployment.<br/>
**To setup a new box, look at server_setup_1.sh and server_setup_2.sh for inspiration**<br/>
Key things to do server side:
* Disable/config selinux
* Set kernel SO_RECVBUF (net.core.rmem_max) to 131072
* Install JDK/JRE 8 and MariaDB
* Use schema.sql in src/main/resources/db/ to get the tables up and running

Things to do client side:
* Check this out from git & compile it with maven (https://maven.apache.org/)
  * To incorporate build hash and number in motd, use **`build.sh`** script. Internally it calls `mvn clean package`
* This will produce tarball `init6.tar.gz` in folder `target`
* Upload + unpack that on your server
* Edit the config files, namely look at
  * init6.conf - this is the main server config file, explanations for important values are in the config
  * start.sh - this is the run script that will drop/restart the server at certain intervals. Editable values at top of the file.
* Once you are done, all you need to do is run `start.sh` to get the server going.
  * Make sure database connection works!

Fun while it lasted! I'm sure we'll be back.