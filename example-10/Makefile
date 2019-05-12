default: run

%.class: %.java
	javac $<

compile: Example.class Util.class BerTlv.class

run: compile
	java Example
