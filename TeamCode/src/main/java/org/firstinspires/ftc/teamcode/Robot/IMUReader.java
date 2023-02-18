package org.firstinspires.ftc.teamcode.Robot;

import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.bosch.JustLoggingAccelerationIntegrator;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.Func;
import org.firstinspires.ftc.robotcore.external.navigation.Acceleration;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.Velocity;

import java.util.Locale;

public class IMUReader implements Runnable{
    // The IMU sensor object
    BNO055IMU imu;
    private boolean terminated;
    private boolean paused;

    // State used for updating telemetry
    Orientation angles;
    Acceleration gravity;
    double headingCorrectionBias;
    private double[] velocity = new double[2];
    private double[] position = new double[2];
    private ElapsedTime dt = new ElapsedTime();
    final double imuHeadingCorrectionFactor = 1;


    public IMUReader(HardwareMap hardwareMap){

        // Set up the parameters with which we will use our IMU. Note that integration
        // algorithm here just reports accelerations to the logcat log; it doesn't actually
        // provide positional information.
        BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
        parameters.angleUnit           = BNO055IMU.AngleUnit.DEGREES;
        parameters.accelUnit           = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
        parameters.calibrationDataFile = "BNO055IMUCalibration.json"; // see the calibration sample opmode
        parameters.loggingEnabled      = true;
        parameters.loggingTag          = "IMU";
        parameters.accelerationIntegrationAlgorithm = new JustLoggingAccelerationIntegrator();

        // Retrieve and initialize the IMU. We expect the IMU to be attached to an I2C port
        // on a Core Device Interface Module, configured to be a sensor of type "AdaFruit IMU",
        // and named "imu".
        imu = hardwareMap.get(BNO055IMU.class, "imu");
        imu.initialize(parameters);

        // Start the logging of measured acceleration
        imu.startAccelerationIntegration(new Position(), new Velocity(), 1000);
    }

    //----------------------------------------------------------------------------------------------
    // Telemetry Configuration
    //----------------------------------------------------------------------------------------------

    public void calibrateIMUHeading() {
        updateIMUStatus();
        headingCorrectionBias = -getRobotHeading();
    }
    public void updateIMUStatus() {
        // Acquiring the angles is relatively expensive; we don't want
        // to do that in each of the three items that need that info, as that's
        // three times the necessary expense.
        angles = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.RADIANS);
        gravity = imu.getGravity();
    }

    public double getRobotHeading() {
        // TODO fix bugs here
        return angles.firstAngle * imuHeadingCorrectionFactor + headingCorrectionBias;
    }
    public double getRobotRoll() {
        return angles.secondAngle;
    }
    public double getRobotPitch() {
        return angles.thirdAngle;
    }

    public double getRobotXAcceleration() {
        return gravity.xAccel;
    }
    public double getRobotYAcceleration() {
        return gravity.yAccel;
    }
    public double getRobotZAcceleration() {
        return getRobotZAcceleration(false);
    }
    public double getRobotZAcceleration(boolean Calibrate) {
        if (Calibrate) return gravity.zAccel - 9.8;
        return gravity.zAccel;
    }
    public String getGravitation() {
        return gravity.toString();
    }

    @Override
    public void run() {
        dt.reset();
        // imu.startAccelerationIntegration(new Position(), new Velocity(), 100);
        imu.stopAccelerationIntegration();
        /*while (!terminated) {
            System.out.print(imu.getPosition().x); System.out.print(" "); System.out.println(imu.getPosition().y);
            double dX, dY;
            // update the imu position
            updateIMUStatus();
            dX = dt.seconds() * velocity[0];
            dY = dt.seconds() * velocity[1];
            velocity[0] += dt.seconds() * getRobotXAcceleration();
            velocity[1] += dt.seconds() * getRobotYAcceleration();
            dX = dt.seconds() * velocity[0];
            dY = dt.seconds() * velocity[1];
            dX /= 2; dY /= 2;
            position[0] += dX;
            position[1] += dY;
            dt.reset(); // calculate the current position, using trapezoid secondary integral of accelration
        } */
        while (!terminated) {
            System.out.print(imu.getPosition().x); System.out.print(" "); System.out.println(imu.getPosition().y);
        }
    }

    public double[] getIMUPosition() {
        return position;
    }

    public void terminate() {
        this.terminated = true;
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }
}
