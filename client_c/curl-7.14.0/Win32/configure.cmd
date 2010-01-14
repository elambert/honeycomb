#!bash
CC=cl
INCLUDE='/CYGDRIVE/C/Program Files/Microsoft Visual C++ Toolkit 2003/include'
LIB='/CYGDRIVE/C/Program Files/Microsoft Visual C++ Toolkit 2003/lib:/CYGDRIVE/C/Program Files/Microsoft Platform SDK/Lib'
./configure --enable-shared=no --disable-ftp --disable-gopher --disable-ldap --disable-dict --disable-telnet --without-ssl --prefix=/opt/honeycomb


#set CC=cl
#set INCLUDE=C:\Program Files\Microsoft Visual C++ Toolkit 2003\include;C:\Program Files\Microsoft Platform SDK\include
#set LIB=C:\Program Files\Microsoft Visual C++ Toolkit 2003\lib;C:\Program Files\Microsoft Platform SDK\Lib
#bash ./configure --enable-shared=no --disable-ftp --disable-gopher --disable-ldap --disable-dict --disable-telnet --without-ssl --prefix=/opt/honeycomb
