SHELL=zsh

pictures:
	java -jar target/reactive-video.jar setup.edn

run: pictures
	open $(target)

t-run:
	lein install
	lein run test.edn

install:
	mkdir bounce
	lein uberjar