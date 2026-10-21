#!/usr/bin/env python3
"""Make an ELF shared library loadable on 16 KB-page Android devices.

For every PT_LOAD segment the Android dynamic linker requires:

    p_offset % PAGE == p_vaddr % PAGE   (PAGE = 16384)

and recommends `p_align >= 16384`.  Prebuilt libraries are usually linked
with 4 KB pages, so the segment file offsets and the `.dynamic` program
header do not line up with their virtual addresses.

This script *inserts* zero padding immediately before each PT_LOAD segment
(preserving every original byte, including inter-segment gaps), then shifts
the file offsets of every affected program header and section header.  It
never rebuilds the file by concatenating segment contents, so relative
offsets such as PT_DYNAMIC stay valid.

Usage:
    align-elf-16k.py <library.so> [output.so]

Prints ALREADY_ALIGNED (exit 0) when no changes are needed, otherwise
ALIGNED (exit 0).
"""
import struct
import sys

PAGE = 16384
PT_LOAD = 1
ELFCLASS32 = 1
ELFCLASS64 = 2

PT_LOAD_OFF = {ELFCLASS32: 4, ELFCLASS64: 8}
P_ALIGN_OFF = {ELFCLASS32: 28, ELFCLASS64: 48}
SH_OFFSET_OFF = {ELFCLASS32: 16, ELFCLASS64: 24}
SHNOBITS = 8


class Elf:
    def __init__(self, data):
        if len(data) < 52 or data[:4] != b'\x7fELF':
            raise ValueError('not an ELF file')
        self.cls = data[4]
        if self.cls == ELFCLASS64:
            self.fmt = '<Q'
            self.e_phoff = struct.unpack_from('<Q', data, 32)[0]
            self.e_shoff = struct.unpack_from('<Q', data, 40)[0]
            self.e_phentsize = struct.unpack_from('<H', data, 54)[0]
            self.e_phnum = struct.unpack_from('<H', data, 56)[0]
            self.e_shentsize = struct.unpack_from('<H', data, 58)[0]
            self.e_shnum = struct.unpack_from('<H', data, 60)[0]
        elif self.cls == ELFCLASS32:
            self.fmt = '<I'
            self.e_phoff = struct.unpack_from('<I', data, 28)[0]
            self.e_shoff = struct.unpack_from('<I', data, 32)[0]
            self.e_phentsize = struct.unpack_from('<H', data, 42)[0]
            self.e_phnum = struct.unpack_from('<H', data, 44)[0]
            self.e_shentsize = struct.unpack_from('<H', data, 46)[0]
            self.e_shnum = struct.unpack_from('<H', data, 48)[0]
        else:
            raise ValueError('unsupported ELF class')
        self.e_phoff_off = 32 if self.cls == ELFCLASS64 else 28
        self.e_shoff_off = 40 if self.cls == ELFCLASS64 else 32
        self.load_off = PT_LOAD_OFF[self.cls]
        self.p_align_off = P_ALIGN_OFF[self.cls]
        self.sh_offset_off = SH_OFFSET_OFF[self.cls]

    def phrase(self, data):
        phdrs = []
        for i in range(self.e_phnum):
            base = self.e_phoff + i * self.e_phentsize
            p_type = struct.unpack_from('<I', data, base)[0]
            p_offset = struct.unpack_from(self.fmt, data, base + self.load_off)[0]
            p_vaddr = struct.unpack_from(self.fmt, data, base + (16 if self.cls == ELFCLASS64 else 8))[0]
            p_align = struct.unpack_from(self.fmt, data, base + self.p_align_off)[0]
            phdrs.append({
                'base': base,
                'type': p_type,
                'offset': p_offset,
                'vaddr': p_vaddr,
                'align': p_align,
            })
        return phdrs


def align(data):
    elf = Elf(data)
    phdrs = elf.phrase(data)
    loads = sorted((p for p in phdrs if p['type'] == PT_LOAD), key=lambda p: p['offset'])

    insertions = []  # (original_offset, padding)
    total = 0
    for load in loads:
        # Offset this segment would get after all previous insertions.
        current = load['offset'] + total
        target_residue = load['vaddr'] % PAGE
        pad = (target_residue - (current % PAGE)) % PAGE
        if pad:
            insertions.append((load['offset'], pad))
            total += pad

    need_align_update = any(p['align'] < PAGE for p in loads)
    if total == 0 and not need_align_update:
        return None

    # Rebuild by inserting padding at the original segment offsets. Every
    # byte of the original file is retained, so gaps and non-PT_LOAD data
    # (e.g. the section header table) keep their relative positions.
    insertions.sort()
    merged = []
    for at, pad in insertions:
        if merged and merged[-1][0] == at:
            merged[-1] = (at, merged[-1][1] + pad)
        else:
            merged.append((at, pad))

    out = bytearray()
    pos = 0
    for at, pad in merged:
        out += data[pos:at]
        out += b'\x00' * pad
        pos = at
    out += data[pos:]

    def shift_at(offset):
        return sum(pad for at, pad in merged if at <= offset)

    e_phoff = elf.e_phoff + shift_at(elf.e_phoff)
    e_shoff = elf.e_shoff + shift_at(elf.e_shoff)

    # Update every program header, not only PT_LOAD: PT_DYNAMIC, PT_NOTE,
    # PT_GNU_RELRO, PT_PHDR, ... must keep pointing at their real data.
    for ph in phdrs:
        base = ph['base'] + shift_at(ph['base'])
        new_offset = ph['offset'] + shift_at(ph['offset'])
        struct.pack_into(elf.fmt, out, base + elf.load_off, new_offset)
        if ph['type'] == PT_LOAD:
            struct.pack_into(elf.fmt, out, base + elf.p_align_off, max(ph['align'], PAGE))

    struct.pack_into(elf.fmt, out, elf.e_phoff_off, e_phoff)
    struct.pack_into(elf.fmt, out, elf.e_shoff_off, e_shoff)

    # Keep section headers pointing at their (shifted) data.
    for i in range(elf.e_shnum):
        base = e_shoff + i * elf.e_shentsize
        if base + elf.e_shentsize > len(out):
            break
        sh_type = struct.unpack_from('<I', out, base + 4)[0]
        if sh_type == SHNOBITS:
            continue
        sh_offset = struct.unpack_from(elf.fmt, out, base + elf.sh_offset_off)[0]
        if sh_offset == 0:
            continue
        struct.pack_into(elf.fmt, out, base + elf.sh_offset_off, sh_offset + shift_at(sh_offset))

    return bytes(out)


def main():
    if len(sys.argv) < 2:
        print('usage: align-elf-16k.py <library.so> [output.so]', file=sys.stderr)
        return 2
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else src
    with open(src, 'rb') as f:
        data = f.read()
    try:
        result = align(data)
    except ValueError as e:
        print('SKIP: %s' % e)
        return 0
    if result is None:
        print('ALREADY_ALIGNED')
        return 0
    with open(dst, 'wb') as f:
        f.write(result)
    print('ALIGNED')
    return 0


if __name__ == '__main__':
    sys.exit(main())
