int f([int] xs) requires no { x in xs | x < 0}:
    return |xs|

void System::main([string] args):
    [int] left = [1,2,3]
    [int] right = [5,6,7]
    int r = f(left + right)
    print str(r)
