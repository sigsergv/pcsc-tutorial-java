default: run

%.class: %.java
	javac $<

compile: Example.class Util.class BerTlv.class BerTlvTest.class

run: compile
	java Example

test-ber-tlv: compile
	java BerTlvTest