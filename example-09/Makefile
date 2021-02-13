default: run

%.class: %.java
	javac $<

compile: BerTlv.class BerTlvTest.class Util.class Example.class

run: compile
	java Example

test-ber-tlv: compile
	java BerTlvTest
