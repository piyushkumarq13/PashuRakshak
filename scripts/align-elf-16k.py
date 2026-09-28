#!/usr/bin/env python3
"""Re-align ELF PT_LOAD segments to a 16KB page boundary.

AGP 8.5.1+ auto-aligns its own native libs, but third-party prebuilt
.so files (e.g. tech.turso.libsql:libsql) often have PT_LOAD segments
that are not 16KB-aligned.  This script rewrites the ELF so that every
PT_LOAD starts at a 16KB offset, by inserting the minimal zero-padding
between segment data blocks and shifting the section-header table.
"""
import sys
import struct

PAGE = 16384
ELFCLASS32 = 1
ELFCLASS64 = 2


def pad_to(out, n):
    out.extend(b'\x00' * n)


def align_file(data):
    if len(data) < 64 or data[:4] != b'\x7fELF':
        return None
    cls = data[4]
    if cls == ELFCLASS64:
        return align_elf64(data)
    if cls == ELFCLASS32:
        return align_elf32(data)
    return None


def align_elf64(b):
    e_phoff = struct.unpack_from('<Q', b, 32)[0]
    e_phentsize = struct.unpack_from('<H', b, 54)[0]
    e_phnum = struct.unpack_from('<H', b, 56)[0]
    e_shoff = struct.unpack_from('<Q', b, 40)[0]
    e_shentsize = struct.unpack_from('<H', b, 58)[0]
    e_shnum = struct.unpack_from('<H', b, 60)[0]

    phs = []
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type = struct.unpack_from('<I', b, base)[0]
        if p_type != 1:
            continue
        p_offset = struct.unpack_from('<Q', b, base + 8)[0]
        p_filesz = struct.unpack_from('<Q', b, base + 32)[0]
        phs.append((i, base, p_offset, p_filesz))
    if not phs:
        return None
    cum = 0
    pads = []
    for (_, __, off, filesz) in phs:
        new_off = off + cum
        pad = (PAGE - (new_off % PAGE)) % PAGE
        pads.append(pad)
        cum += pad
    total_pad = cum
    if total_pad == 0:
        return None
    last_off, last_filesz = phs[-1][2], phs[-1][3]
    last_end = last_off + last_filesz
    if last_end > len(b):
        raise ValueError('segment extends past EOF')
    trailer = b[last_end:]

    out = bytearray()
    for (_, __, off, filesz), pad in zip(phs, pads):
        pad_to(out, pad)
        out.extend(b[off:off + filesz])
    out.extend(trailer)

    struct.pack_into('<Q', out, 32, e_phoff + total_pad)
    struct.pack_into('<Q', out, 40, e_shoff + total_pad)

    ph_shift = pads[0] if phs[0][2] == 0 else 0
    for (i, base, off, _), pad in zip(phs, pads):
        new_base = base + ph_shift
        struct.pack_into('<Q', out, new_base + 8, off + (sum(pads[:i + 1]) - pad))

    acc = 0
    cum_pads = []
    for idx, (_, __, off, _) in enumerate(phs):
        cum_pads.append(acc)
        acc += pads[idx]

    sh_off = e_shoff + total_pad
    for si in range(e_shnum):
        ent = sh_off + si * e_shentsize
        if ent + 32 > len(out):
            break
        sh_offset = struct.unpack_from('<Q', out, ent + 24)[0]
        if sh_offset == 0:
            continue
        add = 0
        for idx, (_, __, off, _) in enumerate(phs):
            if off <= sh_offset < off + phs[idx][3]:
                add = cum_pads[idx]
                break
        else:
            add = total_pad
        struct.pack_into('<Q', out, ent + 24, sh_offset + add)
    return bytes(out)


def align_elf32(b):
    e_phoff = struct.unpack_from('<I', b, 28)[0]
    e_phentsize = struct.unpack_from('<H', b, 42)[0]
    e_phnum = struct.unpack_from('<H', b, 44)[0]
    e_shoff = struct.unpack_from('<I', b, 32)[0]
    e_shentsize = struct.unpack_from('<H', b, 46)[0]
    e_shnum = struct.unpack_from('<H', b, 48)[0]
    phs = []
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type = struct.unpack_from('<I', b, base)[0]
        if p_type != 1:
            continue
        p_offset = struct.unpack_from('<I', b, base + 4)[0]
        p_filesz = struct.unpack_from('<I', b, base + 16)[0]
        phs.append((i, base, p_offset, p_filesz))
    if not phs:
        return None
    cum = 0
    pads = []
    for (_, __, off, filesz) in phs:
        new_off = off + cum
        pad = (PAGE - (new_off % PAGE)) % PAGE
        pads.append(pad)
        cum += pad
    total_pad = cum
    if total_pad == 0:
        return None
    last_off, last_filesz = phs[-1][2], phs[-1][3]
    last_end = last_off + last_filesz
    if last_end > len(b):
        raise ValueError('segment extends past EOF')
    trailer = b[last_end:]
    out = bytearray()
    for (_, __, off, filesz), pad in zip(phs, pads):
        pad_to(out, pad)
        out.extend(b[off:off + filesz])
    out.extend(trailer)
    struct.pack_into('<I', out, 28, e_phoff + total_pad)
    struct.pack_into('<I', out, 32, e_shoff + total_pad)
    ph_shift = pads[0] if phs[0][2] == 0 else 0
    for (i, base, off, _), pad in zip(phs, pads):
        new_base = base + ph_shift
        struct.pack_into('<I', out, new_base + 4, off + (sum(pads[:i + 1]) - pad))
    acc = 0
    cum_pads = []
    for idx, (_, __, off, _) in enumerate(phs):
        cum_pads.append(acc)
        acc += pads[idx]
    sh_off = e_shoff + total_pad
    for si in range(e_shnum):
        ent = sh_off + si * e_shentsize
        if ent + 24 > len(out):
            break
        sh_offset = struct.unpack_from('<I', out, ent + 16)[0]
        if sh_offset == 0:
            continue
        add = 0
        for idx, (_, __, off, _) in enumerate(phs):
            if off <= sh_offset < off + phs[idx][3]:
                add = cum_pads[idx]
                break
        else:
            add = total_pad
        struct.pack_into('<I', out, ent + 16, sh_offset + add)
    return bytes(out)


if __name__ == '__main__':
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else None
    with open(src, 'rb') as f:
        data = f.read()
    res = align_file(data)
    if res is None:
        print('ALREADY_ALIGNED')
        sys.exit(0)
    if dst:
        with open(dst, 'wb') as f:
            f.write(res)
    else:
        with open(src, 'wb') as f:
            f.write(res)
    print('ALIGNED')
