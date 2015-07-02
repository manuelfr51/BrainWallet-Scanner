
bin/BrainwScanner.class: src/BrainwScanner.java
	javac -cp tools/bitcoinj-core-0.12.3-bundled.jar:tools/slf4j-simple-1.6.1.jar\
		-d bin/ src/BrainwScanner.java

clean:
	rm bin/*.class
