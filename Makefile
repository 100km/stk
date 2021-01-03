BINFILES = bin/replicate bin/wipe bin/loader bin/stats bin/sms
DIST = bin.tar.xz
SBT = bin/sbt
GITVER := $(shell git describe --long --always)

all:: assembly

incremental::
	if [ x$(GITVER) != x$$(cat .gitrev 2> /dev/null) ]; then $(MAKE) assembly; fi

.gitrev: ALWAYS
	echo $(GITVER) > .gitrev

assembly:: $(BINFILES)
	$(MAKE) .gitrev

dist:: $(DIST)

$(DIST): $(BINFILES)
	$(RM) $(DIST)
	tar Jcvf $(DIST) $(BINFILES)

clean::
	$(SBT) stk/clean

distclean::
	$(MAKE) clean
	$(RM) $(BINFILES)

check::
	$(SBT) stk/test

$(BINFILES): ALWAYS
	$(SBT) `basename $@`/assembly

ALWAYS::
