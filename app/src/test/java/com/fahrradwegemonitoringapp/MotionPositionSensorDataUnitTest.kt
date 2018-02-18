package com.fahrradwegemonitoringapp

import org.junit.Assert
import org.junit.Test

/**
 * In dieser Klasse wird die Klasse AccelerometerData getestet.
 * Hierfür werden die Berechnungen getestet für den Mittelwert, Varianz und Standardabweichung
 * Created by morro on 15.02.2018.
 */
class MotionPositionSensorDataUnitTest {

    private val mpSensorData : MotionPositionSensorData = MotionPositionSensorData()

    /**
     * Testet die Methode calculateMean mit einer Liste mit den Werten [2.0f, 2.0f]
     * Das erwartete Ergebnis ist 2.0f
     */
    @Test
    fun calculateMean2() {
        val valuesList : MutableList<Float> = mutableListOf(2.0f, 2.0f)
        val mean = mpSensorData.calculateMean(valuesList)
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
        val mean = mpSensorData.calculateMean(valuesList)
        Assert.assertEquals(12.508f, mean)
    }

    /**
     * Testet die Methode calculateMean, wenn die Anzahl der Samples 0 ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateMean0() {
        val valuesList : MutableList<Float> = mutableListOf()
        val mean = mpSensorData.calculateMean(valuesList)
        Assert.assertEquals(0.0f, mean)
    }

    /**
     * Testet die Methode calculateMean, wenn die übergebene Liste Null ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateMeanNullList() {
        val valuesListNull: MutableList<Float>? = null
        val mean = mpSensorData.calculateMean(valuesListNull)
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
        val mean = mpSensorData.calculateMean(valuesList)
        val variance = mpSensorData.calculateVariance(mean, valuesList)
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
        val mean = mpSensorData.calculateMean(valuesList)
        val variance = mpSensorData.calculateVariance(mean, valuesList)
        Assert.assertEquals(234.24704f, variance)
    }

    /**
     * Testet die Methode calculateVariance, wenn die Anzahl der Samples 0 ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateVariance0() {
        val valuesList : MutableList<Float> = mutableListOf()
        val mean = mpSensorData.calculateMean(valuesList)
        val variance = mpSensorData.calculateVariance(mean, valuesList)
        Assert.assertEquals(0.0f, variance)
    }

    /**
     * Testet die Methode calculateVariance, wenn die übergebene Liste Null ist
     * Erwartetes Ergebnis ist 0
     */
    @Test
    fun calculateVarianceNullList() {
        val valuesListNull : MutableList<Float>? = mutableListOf()
        val mean = mpSensorData.calculateMean(valuesListNull)
        val variance = mpSensorData.calculateVariance(mean, valuesListNull)
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
        standardDeviation = mpSensorData.calculateStandardDeviation(x)
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
        standardDeviation = mpSensorData.calculateStandardDeviation(x)
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
        standardDeviation = mpSensorData.calculateStandardDeviation(x)
        Assert.assertEquals(-6.4807405f, standardDeviation)
    }

    /**
     *  Testet die Methode calculateAngelChangeAzimuth.
     *  Als Ergebnis sollte der Winkel 0 Grad haben.
     */
    @Test
    fun calculateAngelChangeAzimuthZero() {
        var azimuthList : MutableList<Float> = mutableListOf(20.0f , 20.0f)
        var result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(0.0f, result)
        azimuthList.clear()
        azimuthList = mutableListOf(-90.0f, -90.0f)
        result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(0.0f, result)
        azimuthList.clear()
        azimuthList = mutableListOf(-90.0f, 42.0f , -90.0f)
        result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(0.0f, result)
    }

    /**
     * Testet den Aufruf der Methode calculateAngelChangeAzimuth mit eineer List die
     * keine Elemente beinhaltet.
     */
    @Test
    fun calculateAngelChangeAzimuthNoElements() {
        var azimuthList : MutableList<Float> = mutableListOf()
        var result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(0.0f, result)
    }

    /**
     * Testet den Aufruf der Methode calculateAngelChangeAzimuth mit mehreren unterschiedlichen Wertem.
     */
    @Test
    fun calculateAngelChangeAzimuthValues() {
        var azimuthList : MutableList<Float> = mutableListOf(0.0f, 10.0f, 20.0f)
        var result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(20.0f, result)
        azimuthList.clear()
        azimuthList = mutableListOf(90.0f, 42.0f , -45.0f)
        result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(135.0f, result)
        azimuthList.clear()
        azimuthList = mutableListOf(10.0f, 42.0f , -45.0f)
        result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(55.0f, result)
        azimuthList.clear()
        azimuthList = mutableListOf(90.0f, 10.0f, 42.0f , -45.0f, -145.0f)
        result = mpSensorData.calculateAngelChangeAzimuth(azimuthList)
        Assert.assertEquals(125.0f, result)
    }

}