# Massive decrease of Docker Image

Before:
```
REPOSITORY          TAG                 IMAGE ID            CREATED                  SIZE
loklak_server       latest              6775047619d8        Less than a second ago   751.6 MB
ubuntu              latest              104bec311bcd        9 days ago               129 MB
```
[Travis Build](https://travis-ci.org/yukiisbored/loklak_server/builds/186665492)

After:
```
REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
loklak_server       latest              1559b163a3dc        1 seconds ago       173.5 MB
alpine              latest              baa5d63471ea        9 weeks ago         4.803 MB
```
[Travis Build](https://travis-ci.org/yukiisbored/loklak_server/builds/186675655)

## How I did it?

This is pretty easy, because it uses Java without having weird native libraries

So to minify it to the extreme, This is how I come up with this:

1. Use a smaller distro/distro that has smaller packages/a distro that focuses
   on being minimal
2. DO NOT USE X
3. Remove development tools because we don't need those
4. Do not create backup files
5. Make it into a single `RUN` command for customizability (so we can remove
   development tools at the end)
6. Use packages that doesn't require X (X is heavy and we don't even require X,
   We can't even use X without doing fancy stuff like an X via SSH)
7. Do not cache anything, good thing Alpine doesn't cache by default
8. Copy only the required files

## What didn't work?

1. GNU Coreutils specific commands/arguments
2. Not having `bash`

## How I made it work?

1. Replace `ps` statements into simple `kill -0` instructions since every
   `POSIX`-compliant has this feature
2. Install `bash`
