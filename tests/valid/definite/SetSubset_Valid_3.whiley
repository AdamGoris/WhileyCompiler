void f({int} xs, {int} ys) requires |xs| <= |ys|:
    if xs ⊂ ys:
        out->println("XS IS A SUBSET")
    else:
        out->println("XS IS NOT A SUBSET")

void System::main([string] args):
    f({1,2},{1,2,3})
    f({1,4},{1,2,3})
    f({1},{1,2,3})
