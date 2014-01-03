import println from whiley.lang.System

type nat is int where $ >= 0

function sum([nat] ls) => nat:
    i = 0
    sum = 0
    while i < |ls| where (i >= 0) && (sum >= 0):
        sum = sum + ls[i]
        i = i + 1
    return sum

method main(System.Console sys) => void:
    sys.out.println(Any.toString(sum([])))
    sys.out.println(Any.toString(sum([1, 2, 3])))
    sys.out.println(Any.toString(sum([12387, 98123, 12398, 12309, 0])))