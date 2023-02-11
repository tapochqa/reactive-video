SHELL=zsh

video:
	ffmpeg -y -framerate 25 -pattern_type glob -i 'target/animation/*.png' -i $(wav) -c:v libx264 -pix_fmt yuv420p $(target)

folders:
	mkdir target/animation
	mkdir target/animation/auto

clean:
	rm -rf target/animation

test:
	echo -n "white" | nc -4u -w0 localhost 1738
	lein compile && lein install
	make folders
	lein run $(wav) $(pic) 0.2
	#mogrify -path target/animation/auto -crop 500x500+0+0 +repage -gravity Center target/animation/*.png
	make video
	make clean
	open $(target)
	echo -n "black" | nc -4u -w0 localhost 1738


install:
	lein uberjar

pictures:
	java -jar target/reactive-video.jar $(wav) $(pic) 0.2

run: clean folders pictures video
	rm -rf target/animation
	open $(target)