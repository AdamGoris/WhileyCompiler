import whiley.lang.System

method main(System.Console sys) => void:
    a1 = [[1, 2, 3], [0]]
    a2 = a1
    a2[0] = [3, 4, 5]
    sys.out.println(Any.toString(a1[0]))
    sys.out.println(Any.toString(a1[1]))
    sys.out.println(Any.toString(a2[0]))
    sys.out.println(Any.toString(a2[1]))
