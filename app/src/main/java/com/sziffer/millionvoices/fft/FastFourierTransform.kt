package com.sziffer.millionvoices.fft


import android.util.Log
import java.lang.Math.*
import kotlin.math.ln


object FastFourierTransform {

    private fun bitReverse(n: Int, bits: Int): Int {

        var n = n
        var reversedN = n
        var count = bits - 1
        //shift
        n = n shr 1
        while (n > 0) {
            reversedN = reversedN shl 1 or (n and 1)
            count--
            n = n shr 1
        }
        return reversedN shl count and (1 shl bits) - 1
    }

    fun fft(input: Array<Complex?>) {

        val bits = (ln(input.size.toDouble()) / ln(2.0)).toInt()

        //creating the proper order for FFT, recursive
        for (j in 1 until input.size / 2) {
            val swapPos =
                bitReverse(
                    j,
                    bits
                )
            val temp = input[j]
            input[j] = input[swapPos]
            input[swapPos] = temp
        }
        //just for knowing the steps
        var stepCounter = 0
        var N = 2
        //butterfly
        while (N <= input.size) {
            //index helper
            var i = 0
            while (i < input.size) {
                for (k in 0 until N / 2) {
                    //calculating the indexes for DFT
                    val evenIndex = i + k
                    val oddIndex = i + k + N / 2
                    //getting the elements
                    val even = input[evenIndex]
                    val odd = input[oddIndex]
                    //calculating the term based on the DFT formula
                    val term: Double = -2 * PI * k / N.toDouble()
                    //in trigonometric form
                    val exp: Complex =
                        Complex(
                            kotlin.math.cos(term),
                            kotlin.math.sin(term)
                        ).multiply(odd!!)

                    input[evenIndex] = even!!.add(exp)
                    input[oddIndex] = even.sub(exp)
                    stepCounter++
                }
                i += N
            }
            //bit shift by 1, << in Java
            N = N shl 1
        }

        Log.i("FFT", "steps done: $stepCounter")
    }
    /** Class for helping calculations */
    class Complex(private val re: Double = 0.0,
                  private val im: Double = 0.0) {

        fun add(b: Complex): Complex {
            return Complex(
                re + b.re,
                im + b.im
            )
        }

        fun sub(b: Complex): Complex {
            return Complex(
                re - b.re,
                im - b.im
            )
        }

        fun multiply(b: Complex): Complex {
            return Complex(
                re * b.re - im * b.im,
                re * b.im + im * b.re
            )
        }

        /** required by frequency estimation */
        fun getR(): Double {
            return kotlin.math.sqrt(re * re + im * im)
        }

    }
}