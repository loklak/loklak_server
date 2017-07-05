#!/usr/bin/env python
"""Script to generate a rst file automatically from an markdown file.

Usage
-----
generate_rst_from_md.py [-h] markdown rst

positional arguments:
  markdown    Path of the source markdown file
  rst         Path where generated rst should be stored

optional arguments:
  -h, --help  show this help message and exit
"""
import argparse
import pypandoc
from sys import platform
import os


def download_pandoc():
    """Download pandoc if not already installed"""
    try:
        # Check whether it is already installed
        pypandoc.get_pandoc_version()
    except OSError:
        # Pandoc not installed. Let's download it.
        pypandoc.download_pandoc()

        # Hack to delete the downloaded file from the folder,
        # otherwise it could get accidently committed to the repo
        # by other scripts in the repo.
        pf = platform
        if pf.startswith('linux'):
            pf = 'linux'
        url = pypandoc.pandoc_download._get_pandoc_urls()[0][pf]
        filename = url.split('/')[-1]
        os.remove(filename)


def convertmd2rst(source, dest):
    download_pandoc()
    pypandoc.convert_file(source, 'rst', outputfile=dest)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('markdown', help='Path of the source markdown file')
    parser.add_argument('rst', help='Path where generated rst should be saved')
    args = parser.parse_args()
    convertmd2rst(args.markdown, args.rst)


if __name__ == '__main__':
    main()
