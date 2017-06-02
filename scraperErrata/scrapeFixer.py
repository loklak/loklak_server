import os
import csv
from pprint import pprint
import fileinput, sys

wordObjectMap = []
content = [content.rstrip('\n') for content in open('erroredWords.csv')]
size = len(content)
pprint(content)

temp = []
count = 0
for word in content:
	wordObjectMap.append(word.split(','))

pprint(wordObjectMap)

def replaceAll(file,searchExp,replaceExp):
	for line in fileinput.input(file, inplace=1):
		if searchExp in line:
			line = line.replace(searchExp,replaceExp)
		sys.stdout.write(line)

for words in wordObjectMap:
	replaceAll("test.json",words[0],words[1])

