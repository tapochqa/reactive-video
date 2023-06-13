SHELL=zsh

pictures:
	java -jar target/reactive-video.jar setup.edn

run: pictures
	open $(target)