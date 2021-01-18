#!/bin/sh

cd ..
[ ! -d "uploads" ] && mkdir uploads

if [ "$#" -ne 1 ] || ! [ -d "src/$1" ]; then
	echo "Usage: $0 PLAYER_DIRECTORY" >&2
	exit 1
fi

cd scripts/
python3 createNoLogSrc.py $1
cd ..

cd src/
zip createdFromScript_$1_nolog.zip $1_nolog -r
mv createdFromScript_$1_nolog.zip ../uploads
echo "Done"
