

# Introduction

This is a clojure-fied version of sample code distributed under the GPL license [here](http://www.surveyor.com/blackfin/#blackfin5)
to interface with the "console" on a Surveyor SRV-1Q robot.

More about this specific robot is online [here.](http://www.inertialabs.com/srv.htm)

My own personal bookmarks related to this robot are collected [here.](http://delicious.com/sfraser/srv-1)


## SRV-1 Control Protocol (Blackfin Version) and firmware

The command interface to the latest version of the robot's firmware is documented [here.](http://www.surveyor.com/SRV_protocol.html)

Note that if you purchase an SRV-1Q you will need to track new releases of the firmware and update your robot. See
[this page](http://www.surveyor.com/blackfin/SRV_setup_bf.html#setup3) for information on how to do this.

The firmware project is online [here.](http://code.google.com/p/surveyor-srv1-firmware/)

## How to run

Make sure you have IP connectivity to the robot. Set the IP and port in SRV1Test.java:

`	public static String SRV_HOST = "169.254.0.10";
	public static int SRV_PORT = 10001;
`

Note that I have only used TCP - I have not tested with UDP yet.

Run the (-main) form in SRV1ClojureConsole.clj and you should connect to the robot.

Once the forms in SRV1ClojureConsole.clj have been compiled, take a look at the examples in ReplToRobotExamples.clj for
some fun functional robot interactions.






