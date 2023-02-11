SHELL=zsh

GAIN = 0.3

video:
	ffmpeg -y -framerate 25 -pattern_type glob -i 'target/animation/*.png' -i "$(wav)" -c:v libx264 -pix_fmt yuv420p -b:a 1411k temp.mp4
	ffmpeg -y -i temp.mp4 -i "target/stale/titles.png" -filter_complex "[0:v][1:v] overlay=x='0':y='0'" -c:v libx264 -pix_fmt yuv420p -b:a 320k "$(target)"
	rm temp.mp4


folders:
	mkdir target/animation
	mkdir target/animation/auto

clean:
	rm -rf target/animation

dev:
	echo -n "white" | nc -4u -w0 localhost 1738
	lein compile && lein install
	make folders
	lein run "$(wav)" "$(pic)" "${GAIN}" "Ты на ней" "Брискович" "Биты 2023"
	#mogrify -path target/animation/auto -crop 500x500+0+0 +repage -gravity Center target/animation/*.png
	make video
	make clean
	open $(target)
	echo -n "black" | nc -4u -w0 localhost 1738


install:
	lein uberjar

pictures:
	java -jar target/reactive-video.jar $(wav) $(pic) ${GAIN}

run: clean folders pictures video
	rm -rf target/animation
	open $(target)