package com.example.morro.fahrradwegemonitoringapp

import com.fahrradwegemonitoringapp.AccelerometerData
import org.junit.Assert
import org.junit.Test

/**
 * In dieser Klasse wird die Klasse AccelerometerData getestet.
 * Hierfür werden die Berechnungen getestet für den Mittelwert, Varianz und Standardabweichung
 * Created by morro on 15.02.2018.
 */
class AccelerometerUnitTest {

    private val accData : AccelerometerData = AccelerometerData()

    /**
     * Testet die Methode calculateMean mit einer Liste mit den Werten [2.0f, 2.0f]
     * Das erwartete Ergebnis ist 2.0f
     */
    @Test
    fun calculateMean2() {
        val valuesList : MutableList<Float> = mutableListOf(2.0f, 2.0f)
        val mean = accData.calculateMean(valuesList)
        Assert.assertEquals(2.0f, mean)
    }

    /**
     * Testet die Methode calculateMean mit einer Liste mit den folgenden Werten:
     * [1.24f, 2.5213f, 10.434f, 42.45f, 5.9f]
     * Das erwartete Ergebnis ist 12.508f
     */
    @Test
    fun calculateMean5() {
        val valuesList : MutableList<Float> = mutableListOf(1.24f, 2.52f, 10.43f, 42.45f, 5.9f)
        val mean = accData.calculateMean(valuesList)
        Assert.assertEquals(12.508f, mean)
    }

    /**
     * Testet die Methode calculateMean, wenn die Anzahl der Samples 0 ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateMean0() {
        val valuesList : MutableList<Float> = mutableListOf()
        val mean = accData.calculateMean(valuesList)
        Assert.assertEquals(0.0f, mean)
    }

    /**
     * Testet die Methode calculateMean, wenn die übergebene Liste Null ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateMeanNullList() {
        val valuesListNull: MutableList<Float>? = null
        val mean = accData.calculateMean(valuesListNull)
        Assert.assertEquals(0.0f, mean)
    }

    /**
     * Testet die Methode calculateVariance mit einer Liste mit den Werten [2.0f, 2.0f]
     * Der Mittelwert sollte 2.0f sein
     * Das erwartete Ergebnis ist 0.0f
     */
    @Test
    fun calculateVariance2() {
        val valuesList : MutableList<Float> = mutableListOf(2.0f, 2.0f)
        val mean = accData.calculateMean(valuesList)
        val variance = accData.calculateVariance(mean, valuesList)
        Assert.assertEquals(0.0f, variance)
    }

    /**
     * Testet die Methode calculateVariance mit einer Liste mit den folgenden Werten:
     * [1.24f, 2.5213f, 10.434f, 42.45f, 5.9f]
     * Der Mittelwert ist 12.508f
     * Die erwartete Varianz ist 234,247016
     */
    @Test
    fun calculatevariance5() {
        val valuesList : MutableList<Float> = mutableListOf(1.24f, 2.52f, 10.43f, 42.45f, 5.9f)
        val mean = accData.calculateMean(valuesList)
        val variance = accData.calculateVariance(mean, valuesList)
        Assert.assertEquals(234.24704f, variance)
    }

    /**
     * Testet die Methode calculateVariance, wenn die Anzahl der Samples 0 ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateVariance0() {
        val valuesList : MutableList<Float> = mutableListOf()
        val mean = accData.calculateMean(valuesList)
        val variance = accData.calculateVariance(mean, valuesList)
        Assert.assertEquals(0.0f, variance)
    }

    /**
     * Testet die Methode calculateVariance, wenn die übergebene Liste Null ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateVarianceNullList() {
        val valuesListNull : MutableList<Float>? = mutableListOf()
        val mean = accData.calculateMean(valuesListNull)
        val variance = accData.calculateVariance(mean, valuesListNull)
        Assert.assertEquals(0.0f, variance)
    }

    /**
     * Dieser Unit Test testet die Methode calculateStandardDeviation.
     * Dabei ist der zu berechnende Wert 0
     */
    @Test
    fun calculateStandardDeviation0() {
        val standardDeviation : Float
        val x = 0.0f
        standardDeviation = accData.calculateStandardDeviation(x)
        Assert.assertEquals(0.0f, standardDeviation)
    }

    /**
     * Dieser Unit Test testet die Methode calculateStandardDeviation.
     * Der zu berechnende Wert ist 3.14
     */
    @Test
    fun calculateStandardDeviation3() {
        val standardDeviation : Float
        val x = 3.14f
        standardDeviation = accData.calculateStandardDeviation(x)
        Assert.assertEquals(1.7720045f, standardDeviation)
    }

    /**
     * Dieser Unit Test testet die Methode calculateStandardDeviation.
     * Der zu berechnende Wert ist 3.14
     */
    @Test
    fun calculateStandardDeviationNegative() {
        val standardDeviation : Float
        val x = -42f
        standardDeviation = accData.calculateStandardDeviation(x)
        Assert.assertEquals(-6.4807405f, standardDeviation)
    }


}