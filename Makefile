NAME = reactive-video
JAR = target/${NAME}.jar
RELEASE = target/${NAME}

pictures:
	java -jar target/reactive-video.jar setup.edn

run: pictures
	open $(target)

t-run:
	lein install
	lein run test.edn bin/macos64/ffmpeg

uberjar:
	lein uberjar

install:
	mkdir -p bounce
	lein uberjar

windows:
	mkdir ${RELEASE}-win64
	mkdir ${RELEASE}-win64/bin
	
	cp ${JAR} ${RELEASE}-win64
	cp conf/start.bat ${RELEASE}-win64
	cp bin/win64/ffmpeg.exe ${RELEASE}-win64/bin
	cp sample.edn ${RELEASE}-win64/setup.edn

linux:
	mkdir ${RELEASE}-linux64
	mkdir ${RELEASE}-linux64/bin
	
	cp ${JAR} ${RELEASE}-linux64
	
	cp conf/start.sh ${RELEASE}-linux64/start
	chmod +x ${RELEASE}-linux64/start
	
	cp bin/linux64/ffmpeg ${RELEASE}-linux64/bin
	cp sample.edn ${RELEASE}-linux64/setup.edn

macos:
	mkdir ${RELEASE}-macos64
	mkdir ${RELEASE}-macos64/bin
	
	cp ${JAR} ${RELEASE}-macos64
	
	cp conf/start.sh ${RELEASE}-macos64/start
	chmod +x ${RELEASE}-macos64/start

	cp bin/macos64/ffmpeg ${RELEASE}-macos64/bin
	cp sample.edn ${RELEASE}-macos64/setup.edn

zip-all:
	zip -r ${RELEASE}-win64.zip ${RELEASE}-win64
	zip -r ${RELEASE}-macos64.zip ${RELEASE}-macos64
	zip -r ${RELEASE}-linux64.zip ${RELEASE}-linux64

release: install windows linux macos
zips: release zip-all


