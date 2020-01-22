#!/usr/bin/env python3

# call i.e. with:
# bin/seperate.py data/dump/own/messages_*.txt.gz
# to run this script on all messages

import sys
import json
import gzip

def separate(filename):

    print("separating " + filename)

    ftoken = filename.split(".")
    gz = ftoken[len(ftoken) - 1] == "gz"
    # if the number of tokens is 2, this is probably a unziped file.
    file_image = ftoken[0] + ".image" if len(ftoken) == 2 else ftoken[0] + ".image." + ftoken[2]
    file_conv0 = ftoken[0] + ".conv0" if len(ftoken) == 2 else ftoken[0] + ".conv0." + ftoken[2]
    file_conv1 = ftoken[0] + ".conv1" if len(ftoken) == 2 else ftoken[0] + ".conv1." + ftoken[2]
    file_puret = ftoken[0] + ".puret" if len(ftoken) == 2 else ftoken[0] + ".puret." + ftoken[2]

    with \
         gzip.open(filename, mode="rt", encoding="utf-8") if gz else open(filename, "r", encoding="utf-8") as text_in, \
         gzip.open(file_image, mode="wt", encoding="utf-8") if gz else open(file_image, "a", encoding="utf-8") as image_out, \
         gzip.open(file_conv0, mode="wt", encoding="utf-8") if gz else open(file_conv0, "a", encoding="utf-8") as conv0_out, \
         gzip.open(file_conv1, mode="wt", encoding="utf-8") if gz else open(file_conv1, "a", encoding="utf-8") as conv1_out, \
         gzip.open(file_puret, mode="wt", encoding="utf-8") if gz else open(file_puret, "a", encoding="utf-8") as puret_out:

        for line in text_in:
            j = json.loads(line)
            if not 'text' in j: continue
            if 'images' in j:
                images = j['images']
                if len(images) > 0:
                    image_out.write(line)

                else:
                    if 'links_count' in j and j['links_count'] == 0:
                        if 'mentions_count' in j:
                            mentions_count = j['mentions_count']

                        if mentions_count == 1:
                            conv1_out.write(line)

                        if mentions_count == 0:
                            conv0_out.write(line)
                            if 'hashtags_count' in j:
                                hashtags_count = j['hashtags_count']
                                if hashtags_count == 0:
                                    puret_out.write(line)

sys.argv.pop(0)
for f in sys.argv: separate(f)

