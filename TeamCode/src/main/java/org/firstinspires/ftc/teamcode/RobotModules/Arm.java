/*
 * Copyright © 2023 SCCSC-Robotics-Club
 * FileName: ArmControllingMethods.java
 *
 * the program that controls the elevation of the arm and operations of the claw
 *
 * @Author 四只爱写代码の猫
 * @Date 2023.2.27
 * @Version v0.1.0
 * */
package org.firstinspires.ftc.teamcode.RobotModules;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.HardwareDriver;
import org.firstinspires.ftc.teamcode.RobotModule;

import java.util.HashMap;

public class Arm extends RobotModule {
    /** highest position of the arm */
    private final int highPos = 700;
    /** midpoint position of the arm */
    private final int midPos = 450;
    /** lower position of the arm */
    private final int lowPos = 280;
    /** loading position of the arm */
    private final int gndPos = 45;

    /** connects to the hardware */
    private HardwareDriver hardwareDriver;

    /** connects to the game pad that's used to control the arm */
    private Gamepad gamepad;

    /** whether the claw is opened */
    private boolean claw;

    /** the position code for the arm
    *  -1: the arm is relaxed
    *   0: the arm is attached to the ground to capture sleeves,
    *   1: the arm is at the position matching the lowest tower,
    *   2: the arm is at the position matching the middle tower,
    *   3: the arm is at the position matching the highest tower,
    */
    private short armStatusCode;

    /** the status of the arm
    *   true: the arm is busy and in use
    *   false: the arm is free, no operation is proceeding
    */
    private boolean armIsBusy;

    /** variables that record the time after pressing a button, so that the button is not activated over and over */
    private final ElapsedTime PreviousElevatorActivation = new ElapsedTime();
    private final ElapsedTime PreviousClawActivation = new ElapsedTime();
    private final ElapsedTime PreviousGrepActivation = new ElapsedTime();

    /**
     * construct function of arm controlling methods
     * set the module's name to be "Arm"
     *
     */
    public Arm() {
        super("Arm");
    }

    /**
     * initialize the arm module
     * given all the instances that connects to the robot's hardware
     *
     * @param dependentModules null should be passed as this module is not using any other modules as dependencies
     * @param dependentInstances this module need the following instances(pass them in the form of hashmap):
     *                           "hardwareDriver" : HardwareDriver, the driver that connects to the hardware, gained from super class "LinearOpMode"
     *                           "initialControllerPad" : com.qualcomm.robotcore.hardware.Gamepad, the default game pad used to control the robot
     */
    @Override
    public void init(HashMap<String, RobotModule> dependentModules, HashMap<String, Object> dependentInstances) {
        /* get the instances from the param */
        this.hardwareDriver = (HardwareDriver) dependentInstances.get("hardwareDriver");
        this.gamepad = (Gamepad) dependentInstances.get("initialControllerPad");

        /* set the robot's arm to be the default status */
        hardwareDriver.claw.setPosition(0.6);
        this.claw = false;
        deactivateArm();
        this.armStatusCode = -1;
    }

    @Override
    public void periodic() throws InterruptedException {
        if (gamepad.right_bumper) closeClaw();
        else if (gamepad.left_bumper) this.openClaw();

        if (gamepad.y) {
            this.toHighArmPosition();
        }
        if (gamepad.x) {
            this.toMidArmPosition();
        }
        if (gamepad.b) {
            this.toLowArmPosition();
        }
        if (gamepad.a) {
            this.toGroundArmPosition();
        }
        if (gamepad.right_trigger>0.2 & PreviousGrepActivation.seconds() > .3) {
            PreviousGrepActivation.reset();
            this.openClaw();
            this.deactivateArm();
            robotChassis.pause();
            // TODO aim the target automatically using computer vision
            robotChassis.resume();
            this.closeClaw();
            Thread.sleep(300);
            this.toMidArmPosition();
        }

        if (gamepad.left_stick_y < -0.8 & PreviousElevatorActivation.seconds() > .3) { // the elevator cannot be immediately activated until 0.2 seconds after the last activation
            System.out.println("RA");
            this.raiseArm();
            PreviousElevatorActivation.reset();
        } else if (gamepad.left_stick_y > 0.8 & PreviousElevatorActivation.seconds() > .3) {
            System.out.println("LA");
            this.lowerArm();
            PreviousElevatorActivation.reset();
        }

        if (PreviousElevatorActivation.seconds() > 30 & robotChassis.getLastMovementTime() > 30 & PreviousClawActivation.seconds() > 30) { // no operation after 30s
            hardwareDriver.lift_left.setPower(0);
            hardwareDriver.lift_left.setPower(0);
            System.exit(0);
        } if (PreviousElevatorActivation.seconds() > 5 & this.getClaw()) {
            System.out.println("saving battery...");
            this.deactivateArm(); // deactivate when no use for 5 seconds so that the motors don't overheat
            PreviousElevatorActivation.reset(); // so that it does not proceed deactivate all the time
        }

        // control slow motion automatically
        if (this.getArmIsBusy()) robotChassis.setSlowMotionModeActivationSwitch(true);
        else robotChassis.setSlowMotionModeActivationSwitch(false);
    }

    /**
     * move the arm down into the following lower level
     */
    public void lowerArm() {
        System.out.println(this);
        switch (armStatusCode) {
            case 3: toMidArmPosition(); break;
            case 2: toLowArmPosition(); break;
            case 1: toGroundArmPosition(); break;
            case 0: deactivateArm(); break;
        }
    }

    /**
     * move the arm up into the higher neighboured level
     */
    public void raiseArm() {
        switch (armStatusCode) {
            case -1: case 0: toLowArmPosition(); break;
            case 1: toMidArmPosition(); break;
            case 2: toHighArmPosition(); break;
        }
    }

    /**
     * set the arm to match the higher tower
     */
    public void toHighArmPosition() {
        elevateArm(highPos);
        armStatusCode = 3;
        armIsBusy = true;
    }
    /**
     * set the arm to match the higher, middle and lowest tower
     */
    public void toMidArmPosition() {
        elevateArm(midPos);
        armStatusCode = 2;
        armIsBusy = true;
    }
    /**
     * set the arm to match the higher, middle and lowest tower
     */
    public void toLowArmPosition() {
        elevateArm(lowPos);
        armStatusCode = 1;
        armIsBusy = true;
    }

    public void toGroundArmPosition() {
        elevateArm(gndPos);
        armStatusCode = 0;
        armIsBusy = false;
    }

    /**
     * move the lowest position of the arm to load sleeves
     *
     * @Deprecated this method is longer suggested, use deactivateArm() instead
     */
    @Deprecated
    public void toLoadingArmPosition() {
        // go to the arm position to load the sleeves
        int loadingPos = 360;
        elevateArm(loadingPos);
        armStatusCode = 1;
        armIsBusy = true;
    }

    /**
     * if the claw is opened, close it
     * if the claw is closed, open it
     */
    public void open_closeClaw() {
        System.out.println("open_close");
        if(claw) {closeClaw(); return;}
        openClaw();
    }

    /**
     * open the claw of the arm by setting the position of the servo driving it
     */
    public void openClaw() {
        System.out.println("opening");
        claw = true;
        hardwareDriver.claw.setPosition(.35); // open grabber
        // while (Math.abs(hr.claw.getPosition() - .35) > .05) Thread.yield(); // wait until the movement is finished, accept any inaccuracy below 5%
        armIsBusy = false;
    }
    /**
     * close the claw of the arm by setting the position of the servo driving it
     */
    public void closeClaw() {
        System.out.println("closing");
        claw = false;
        hardwareDriver.claw.setPosition(.65); // close grabber
        // while (Math.abs(hr.claw.getPosition() - .65) > .05) Thread.yield();
        armIsBusy = true;
    }

    /**
     * move the arm to the targeted position
     * when the arm is almost there, reverse the motors and decelerate to avoid damage to the structer
     * exit the function when the arm is close enough to the objective
     *
     * @param position: the targeted position, ranged 0-1000, 0 is when the arm hits the robot badly, 1000 is when the arm flips around and damage the structer
     */
    private void elevateArm(int position) {
        /** the direction that the arm is going
        *   true when the arm is going up
        *   false when the arm is going down
        */
        boolean isDecline = position < hardwareDriver.lift_left.getCurrentPosition();

        /* set the power of the motor
        *   20% when it's going down, in considerate of the impulse of gravitation
        *   40% when it's going up
        *  */
        if (isDecline) {
            double armDeclineSpeed = 0.2;
            hardwareDriver.lift_left.setPower(armDeclineSpeed);
            hardwareDriver.lift_right.setPower(armDeclineSpeed);
        } else {
            double armInclineSpeed = 0.4;
            hardwareDriver.lift_left.setPower(armInclineSpeed);
            hardwareDriver.lift_right.setPower(armInclineSpeed);
        }

        /*
        * set the targeted position of the motors
        * set the running mode
        * */
        hardwareDriver.lift_left.setTargetPosition(position);
        hardwareDriver.lift_left.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        hardwareDriver.lift_right.setTargetPosition(position);
        hardwareDriver.lift_right.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        /* if the arm isn't declining
         *  move to the position directly, as gravity will slow the arm down
         */
        if (!isDecline) {
            while (Math.abs(hardwareDriver.lift_left.getCurrentPosition()-position) > 5 | Math.abs(hardwareDriver.lift_right.getCurrentPosition()-position) > 5) Thread.yield(); // wait until the movement is completed
            return;
        }

        /* if the arm is declining
         * deceleration precess is necessary
         */
        while (Math.abs(hardwareDriver.lift_left.getCurrentPosition()-position) > 20 | Math.abs(hardwareDriver.lift_right.getCurrentPosition()-position) > 20) Thread.yield(); // wait until the movement almost complete
        /* slow the motor down */
        hardwareDriver.lift_left.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        hardwareDriver.lift_right.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        hardwareDriver.lift_right.setVelocity(0);
        hardwareDriver.lift_left.setVelocity(0);
        while (Math.abs(hardwareDriver.lift_left.getVelocity()) < 10) Thread.yield(); // wait until the slow-down is completed, accept any deviation less than 3
        /* set the desired position */
        hardwareDriver.lift_left.setTargetPosition(position);
        hardwareDriver.lift_left.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        hardwareDriver.lift_right.setTargetPosition(position);
        hardwareDriver.lift_right.setMode(DcMotor.RunMode.RUN_TO_POSITION); // make the motor stick in the position
    }

    public void deactivateArm() {
        /* if (
                arm == -1 |
                (!claw) // if the claw is set to be closed
        ) return; // if the arm is already deactivated, or if the claw is holding stuff, abort */
        while (armStatusCode > 0) lowerArm(); // put the arm down step by step
        openClaw();
        hardwareDriver.lift_left.setPower(0);
        hardwareDriver.lift_right.setPower(0);
        armStatusCode = -1;
    }

    public boolean getClaw() {
        return claw;
    }

    public boolean getArmIsBusy() {
        /* true: busy, false: free */
        return armIsBusy;
    }
}
