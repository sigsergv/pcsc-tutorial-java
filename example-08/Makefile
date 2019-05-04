default: run

%.class: %.java
	javac $<

compile: Example.class Util.class

run: compile
	java Example