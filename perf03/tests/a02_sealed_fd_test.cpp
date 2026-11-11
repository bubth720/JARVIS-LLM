#include "../native/a02_sealed_fd.h"
#include <cassert>
#include <cstring>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <string>
int main() {
    int fd=memfd_create("jarvis-a02-test",MFD_ALLOW_SEALING|MFD_CLOEXEC);
    assert(fd>=0);
    const char bytes[]="test-model";
    assert(write(fd,bytes,sizeof(bytes))==sizeof(bytes));
    bool refused=false;
    try { int wrong=jarvis_a02::duplicate_verified_fd(fd,sizeof(bytes));close(wrong); }
    catch(const std::exception&) { refused=true; }
    assert(refused);
    assert(fcntl(fd,F_ADD_SEALS,jarvis_a02::kRequiredSeals)==0);
    int owned=jarvis_a02::duplicate_verified_fd(fd,sizeof(bytes));
    close(fd);
    char readback[sizeof(bytes)]={};
    assert(pread(owned,readback,sizeof(bytes),0)==sizeof(bytes));
    assert(std::memcmp(readback,bytes,sizeof(bytes))==0);
    assert(pwrite(owned,"X",1,0)==-1);
    assert(ftruncate(owned,0)==-1);
    close(owned);
    return 0;
}
