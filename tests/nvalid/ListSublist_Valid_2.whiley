import println from whiley.lang.System

type posintlist is [int]

function sum(posintlist ls) => int:
    if |ls| == 0:
        return 0
    else:
        rest = ls[1..|ls|]
        return ls[0] + sum(rest)

method main(System.Console sys) => void:
    c = sum([1, 2, 3, 4, 5, 6, 7])
    sys.out.println(Any.toString(c))