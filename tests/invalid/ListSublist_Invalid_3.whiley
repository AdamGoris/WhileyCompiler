import * from whiley.lang.*

method main(System.Console sys) => void:
    list = [1, 2, 3]
    sublist = list[2..x]
    sys.out.println(Any.toString(list))
    sys.out.println(Any.toString(sublist))
