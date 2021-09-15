def findMedian(arr: Array[Int]): Int = {


    val x = arr.sorted
    x(arr.length / 2)
}




def flippingMatrix(matrix: Array[Array[Int]]): Int = {
     matrix.flatten.sorted.takeRight(matrix.length).sum
}

