package com.fahrradwegemonitoringapp

import org.junit.Assert
import org.junit.Test
import java.lang.Double.NaN

/**
 * In dieser Klasse wird die Klasse AccelerometerData getestet.
 * Hierfür werden die Berechnungen getestet für den Mittelwert, Varianz und Standardabweichung
 * Created by morro on 15.02.2018.
 */
class MotionPositionSensorDataUnitTest {

    private val mpSensorData : MotionPositionSensorData = MotionPositionSensorData()

    /**
     * Testet die Funktion getTimeframeIndecis mit zwei sehr kleinen Werten die Zeitstempel repräsentieren
     */
    @Test
    fun getTimeframeIndecis2() {
        val timestamps : MutableList<Long> = mutableListOf(100, 200)
        val indecis= mpSensorData.getTimeframeIndecis(timestamps, 150,10)
        Assert.assertEquals(0, indecis[0])
        Assert.assertEquals(1, indecis[1])
    }

    /**
     * Testet die Funktion getTimeframeIndecis mit zwei einer Liste aus realistischen Zeitstempeln
     */
    @Test
    fun getTimeframeIndecisRealistic() {
        val timestamps : MutableList<Long> = mutableListOf(15000000000, 15010000000,15030000000,
                15060000000,15065000000,15100000000)
        val indecis= mpSensorData.getTimeframeIndecis(timestamps, 15030000000,10000000)
        Assert.assertEquals(0, indecis[0])
        Assert.assertEquals(4, indecis[1])
    }

    /**
     * Testet die Funktion getTimeframeIndecis. Dabei wird getestet ob der erste
     * Messwert korrekt erfasst wird.
     */
    @Test
    fun getTimeframeIndecisBegin() {
        val timestamps : MutableList<Long> = mutableListOf(15000000000, 15010000000,15030000000,
                15060000000,15080000000,15100000000)
        val indecis= mpSensorData.getTimeframeIndecis(timestamps, 15020000000,40000000)
        Assert.assertEquals(0, indecis[0])
        Assert.assertEquals(4, indecis[1])
    }

    /**
     * Testet die Funktion getTimeframeIndecis mit zwei einer leeren Liste
     */
    @Test
    fun getTimeframeIndecisEmpty() {
        val timestamps : MutableList<Long> = mutableListOf()
        val indecis= mpSensorData.getTimeframeIndecis(timestamps, 15030000000,10000000)
        Assert.assertEquals(0, indecis[0])
        Assert.assertEquals(0, indecis[1])
    }

    /**
     * Testet die Funktion getTimeframeIndecis mit zwei einer null Liste
     */
    @Test
    fun getTimeframeIndecisNull() {
        val timestamps : MutableList<Long>? = null
        val indecis= mpSensorData.getTimeframeIndecis(timestamps, 15030000000,10000000)
        Assert.assertEquals(0, indecis[0])
        Assert.assertEquals(0, indecis[1])
    }

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
}