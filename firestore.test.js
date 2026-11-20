const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PASSENGER_UID = "passenger_ali";
const OTHER_PASSENGER_UID = "passenger_sana";
const DRIVER_UID = "driver_arslan";
const UNAPPROVED_DRIVER_UID = "driver_unapproved";
const OWNER_UID = "ADMIN-MAHI";
const OWNER_EMAIL = "khokharbilal665@gmail.com";
const OWNER_PHONE = "+923429348582";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// =========================================================================
// 1. UNAUTHENTICATED & PUBLIC CONFIG ACCESS
// =========================================================================

test("Unauthenticated user: denied from reading private collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("drivers").get());
  await assertFails(unauthDb.collection("rides").get());
  await assertFails(unauthDb.collection("ride_requests").get());
  await assertFails(unauthDb.collection("scheduled_rides").get());
  await assertFails(unauthDb.collection("security_audit_logs").get());
  await assertFails(unauthDb.collection("notification_events").get());
  await assertFails(unauthDb.collection("config").doc("security").get());
});

test("Public fare config (/config/fares) is readable, /config/security is owner-only", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("config").doc("fares").set({
      bikeBaseFare: 70,
      rickshawBaseFare: 120,
      carBaseFare: 250,
    });
    await ctx.firestore().collection("config").doc("security").set({
      ownerPasscodeHash: "salted_hash_123",
      ownerPasscodeSalt: "salt_abc",
    });
  });

  const passengerDb = testEnv.authenticatedContext(PASSENGER_UID).firestore();
  await assertSucceeds(passengerDb.collection("config").doc("fares").get());
  await assertFails(passengerDb.collection("config").doc("security").get());
  await assertFails(
    passengerDb.collection("config").doc("security").set({ ownerPasscodeHash: "hacked" })
  );

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(ownerDb.collection("config").doc("security").get());
  await assertSucceeds(
    ownerDb.collection("config").doc("security").set({
      ownerPasscodeHash: "salted_hash_456",
      ownerPasscodeSalt: "salt_def",
    })
  );
});

// =========================================================================
// 2. OWNER IDENTITY LOCK & PRIVILEGE ESCALATION PROTECTION
// =========================================================================

test("Non-owner cannot self-elevate to ADMIN role in /users or write /admins", async () => {
  const intruderDb = testEnv.authenticatedContext(PASSENGER_UID, {
    email: "intruder@gmail.com",
    email_verified: true,
  }).firestore();

  // Attempt to create user profile with ADMIN role
  await assertFails(
    intruderDb.collection("users").doc(PASSENGER_UID).set({
      uid: PASSENGER_UID,
      name: "Intruder",
      role: "ADMIN",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Create normal PASSENGER profile succeeds
  await assertSucceeds(
    intruderDb.collection("users").doc(PASSENGER_UID).set({
      uid: PASSENGER_UID,
      name: "Ali Passenger",
      role: "PASSENGER",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Attempt to update role from PASSENGER to ADMIN fails
  await assertFails(
    intruderDb.collection("users").doc(PASSENGER_UID).update({
      role: "ADMIN",
      updatedAt: nowTs(),
    })
  );

  // Attempt to write /admins fails
  await assertFails(
    intruderDb.collection("admins").doc(PASSENGER_UID).set({
      uid: PASSENGER_UID,
      name: "Fake Admin",
      email: OWNER_EMAIL,
      role: "ADMIN",
      createdAt: nowTs(),
    })
  );
});

test("MAHI Owner identity works via verified Gmail or verified Owner phone", async () => {
  const emailOwnerDb = testEnv.authenticatedContext("uid_mahi_gmail", {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    emailOwnerDb.collection("admins").doc("ADMIN-MAHI").set({
      uid: "ADMIN-MAHI",
      name: "Bilal Khokhar (MAHI)",
      email: OWNER_EMAIL,
      role: "ADMIN",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  const phoneOwnerDb = testEnv.authenticatedContext("uid_mahi_phone", {
    phone_number: OWNER_PHONE,
  }).firestore();

  await assertSucceeds(
    phoneOwnerDb.collection("config").doc("fares").set({
      bikeBaseFare: 80,
      rickshawBaseFare: 130,
      carBaseFare: 260,
    })
  );
  await assertSucceeds(phoneOwnerDb.collection("config").doc("security").get());

  // Unverified owner email MUST BE DENIED
  const unverifiedEmailDb = testEnv.authenticatedContext("uid_spoof", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(unverifiedEmailDb.collection("config").doc("security").get());
});

// =========================================================================
// 3. CROSS-USER ISOLATION: PROFILES, SAVED PLACES, FCM TOKENS
// =========================================================================

test("Cross-user isolation on /users, /saved_places, and /fcmTokens", async () => {
  const aliDb = testEnv.authenticatedContext(PASSENGER_UID).firestore();
  const sanaDb = testEnv.authenticatedContext(OTHER_PASSENGER_UID).firestore();

  await assertSucceeds(
    aliDb.collection("users").doc(PASSENGER_UID).set({
      uid: PASSENGER_UID,
      name: "Ali",
      role: "PASSENGER",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    aliDb.collection("users").doc(PASSENGER_UID).collection("fcmTokens").doc("token_ali_12345").set({
      token: "token_ali_12345",
      platform: "android",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    aliDb.collection("users").doc(PASSENGER_UID).collection("saved_places").doc("home_1").set({
      id: "home_1",
      label: "Home",
      address: "Committee Chowk, Jaranwala",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Cross-user access fails
  await assertFails(sanaDb.collection("users").doc(PASSENGER_UID).get());
  await assertFails(
    sanaDb.collection("users").doc(PASSENGER_UID).collection("fcmTokens").doc("token_ali_12345").get()
  );
  await assertFails(
    sanaDb.collection("users").doc(PASSENGER_UID).collection("fcmTokens").doc("token_sana_9999").set({
      token: "token_sana_9999",
      platform: "android",
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    sanaDb.collection("users").doc(PASSENGER_UID).collection("saved_places").doc("home_1").get()
  );

  // Owner updates display name, phoneNumber, phone, and emergencyContact cleanly (preserving createdAt/role/uid)
  await assertSucceeds(
    aliDb.collection("users").doc(PASSENGER_UID).update({
      name: "Ali Raza Updated",
      phoneNumber: "0301-9988776",
      phone: "0301-9988776",
      emergencyContact: "0342-9348582",
      updatedAt: nowTs(),
    })
  );

  // Cross-user profile update MUST FAIL
  await assertFails(
    sanaDb.collection("users").doc(PASSENGER_UID).update({
      emergencyContact: "0300-0000000",
      updatedAt: nowTs(),
    })
  );

  // Oversized emergencyContact (> 30 chars) MUST FAIL
  await assertFails(
    aliDb.collection("users").doc(PASSENGER_UID).update({
      emergencyContact: "0".repeat(31),
      updatedAt: nowTs(),
    })
  );
});

// =========================================================================
// 4. DRIVER APPROVAL & ATOMIC DISPATCH CLAIM PROTECTION
// =========================================================================

test("Driver cannot self-approve; only MAHI Owner can approve driver", async () => {
  const driverDb = testEnv.authenticatedContext(UNAPPROVED_DRIVER_UID).firestore();

  // Driver registration creates unapproved record (isApproved: false)
  await assertSucceeds(
    driverDb.collection("drivers").doc(UNAPPROVED_DRIVER_UID).set({
      driverId: UNAPPROVED_DRIVER_UID,
      name: "Imran Driver",
      phone: "0300-1234567",
      vehicleType: "RICKSHAW",
      isOnline: false,
      isApproved: false,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Driver cannot create pre-approved profile
  await assertFails(
    testEnv.authenticatedContext("driver_cheater").firestore()
      .collection("drivers").doc("driver_cheater").set({
        driverId: "driver_cheater",
        name: "Cheater",
        phone: "0300-9999999",
        vehicleType: "CAR",
        isOnline: true,
        isApproved: true,
        createdAt: nowTs(),
        updatedAt: nowTs(),
      })
  );

  // Driver cannot self-update isApproved
  await assertFails(
    driverDb.collection("drivers").doc(UNAPPROVED_DRIVER_UID).update({
      isApproved: true,
      updatedAt: nowTs(),
    })
  );

  // Owner MAHI approves driver
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    ownerDb.collection("drivers").doc(UNAPPROVED_DRIVER_UID).update({
      isApproved: true,
      updatedAt: nowTs(),
    })
  );
});

test("Approved driver atomically claims SEARCHING ride; unapproved driver & hijacks denied", async () => {
  // Seed an approved driver and an unapproved driver
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("drivers").doc(DRIVER_UID).set({
      driverId: DRIVER_UID,
      name: "Captain Arslan",
      phone: "0300-1122334",
      vehicleType: "RICKSHAW",
      isOnline: true,
      isApproved: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await ctx.firestore().collection("drivers").doc(UNAPPROVED_DRIVER_UID).set({
      driverId: UNAPPROVED_DRIVER_UID,
      name: "Captain Pending",
      phone: "0300-7777777",
      vehicleType: "RICKSHAW",
      isOnline: false,
      isApproved: false,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const passengerDb = testEnv.authenticatedContext(PASSENGER_UID).firestore();
  const approvedDriverDb = testEnv.authenticatedContext(DRIVER_UID).firestore();
  const unapprovedDriverDb = testEnv.authenticatedContext(UNAPPROVED_DRIVER_UID).firestore();

  // Passenger creates SEARCHING ride request
  await assertSucceeds(
    passengerDb.collection("ride_requests").doc("JRW-9001").set({
      passengerId: PASSENGER_UID,
      pickupLocation: "Committee Chowk",
      destinationLocation: "Railway Station Jaranwala",
      rideType: "RICKSHAW",
      status: "SEARCHING",
      estimatedFarePKR: 180,
      distanceKm: 2.5,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Unapproved driver cannot read or claim SEARCHING ride
  await assertFails(unapprovedDriverDb.collection("ride_requests").doc("JRW-9001").get());
  await assertFails(
    unapprovedDriverDb.collection("ride_requests").doc("JRW-9001").update({
      status: "ACCEPTED",
      driverId: UNAPPROVED_DRIVER_UID,
      driverName: "Captain Pending",
      driverPhone: "0300-7777777",
      vehiclePlate: "FSD-111",
      updatedAt: nowTs(),
    })
  );

  // Approved driver cannot tamper with fare while claiming
  await assertFails(
    approvedDriverDb.collection("ride_requests").doc("JRW-9001").update({
      status: "ACCEPTED",
      driverId: DRIVER_UID,
      estimatedFarePKR: 5000,
      updatedAt: nowTs(),
    })
  );

  // Approved driver legitimately claims SEARCHING ride
  await assertSucceeds(
    approvedDriverDb.collection("ride_requests").doc("JRW-9001").update({
      status: "ACCEPTED",
      driverId: DRIVER_UID,
      driverName: "Captain Arslan",
      driverPhone: "0300-1122334",
      vehiclePlate: "FS-23-4412",
      updatedAt: nowTs(),
    })
  );

  // Assigned driver updates live GPS and completes ride with Cash collected
  await assertSucceeds(
    approvedDriverDb.collection("ride_requests").doc("JRW-9001").update({
      driverLat: 31.3351,
      driverLng: 73.4188,
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    approvedDriverDb.collection("ride_requests").doc("JRW-9001").update({
      status: "COMPLETED",
      paymentMethod: "CASH",
      paymentStatus: "COLLECTED_CASH",
      completedAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

// =========================================================================
// 5. NOTIFICATION EVENTS & TAMPER-PROOF AUDIT LOGS
// =========================================================================

test("Notification events enforce senderUid == auth.uid and audit logs are append-only", async () => {
  const passengerDb = testEnv.authenticatedContext(PASSENGER_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  // Forged senderUid fails
  await assertFails(
    passengerDb.collection("notification_events").doc("evt_forged").set({
      senderUid: "ADMIN-MAHI",
      recipientUid: DRIVER_UID,
      type: "NEW_RIDE_REQUEST",
      title: "Fake",
      body: "Spoofed event",
      createdAt: nowTs(),
    })
  );

  // Valid notification event succeeds
  await assertSucceeds(
    passengerDb.collection("notification_events").doc("evt_valid").set({
      senderUid: PASSENGER_UID,
      recipientUid: "DRIVERS_BROADCAST",
      type: "NEW_RIDE_REQUEST",
      title: "New Ride",
      body: "Committee Chowk -> Railway Station",
      rideId: "JRW-9001",
      targetTab: 1,
      createdAt: nowTs(),
    })
  );

  // Audit logs: signed-in user can append their own log, cannot read/update/delete; owner can read only
  await assertSucceeds(
    passengerDb.collection("security_audit_logs").doc("log_1").set({
      id: "log_1",
      userId: PASSENGER_UID,
      eventType: "LOGIN_SUCCESS",
      description: "Passenger logged in",
      severity: "NORMAL",
      timestamp: nowTs(),
    })
  );
  await assertFails(passengerDb.collection("security_audit_logs").doc("log_1").get());
  await assertFails(
    ownerDb.collection("security_audit_logs").doc("log_1").update({ description: "Tampered" })
  );
  await assertFails(ownerDb.collection("security_audit_logs").doc("log_1").delete());
  await assertSucceeds(ownerDb.collection("security_audit_logs").doc("log_1").get());
});

// =========================================================================
// 6. SCHEDULED RIDES (/scheduled_rides/{scheduleId})
// =========================================================================

test("Scheduled rides enforce passenger ownership, validation, cross-user isolation, and approved driver dispatch", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("drivers").doc(DRIVER_UID).set({
      driverId: DRIVER_UID,
      name: "Captain Arslan",
      phone: "0300-1122334",
      vehicleType: "CAR",
      isOnline: true,
      isApproved: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const aliDb = testEnv.authenticatedContext(PASSENGER_UID).firestore();
  const sanaDb = testEnv.authenticatedContext(OTHER_PASSENGER_UID).firestore();
  const approvedDriverDb = testEnv.authenticatedContext(DRIVER_UID).firestore();
  const futureEpochMs = Date.now() + 3600 * 1000;

  // Reject creating scheduled ride with pre-set driverId
  await assertFails(
    aliDb.collection("scheduled_rides").doc("SCH-BAD-1").set({
      scheduleId: "SCH-BAD-1",
      passengerId: PASSENGER_UID,
      passengerName: "Ali",
      passengerPhone: "0300-1111111",
      pickupLocation: "Committee Chowk",
      destinationLocation: "Railway Station",
      rideType: "CAR",
      vehicleCategoryTitle: "Car Ride (AC)",
      estimatedFarePKR: 320,
      distanceKm: 3.1,
      scheduledForMillis: futureEpochMs,
      scheduledDateDisplay: "15 Nov 2026, 04:00 PM",
      status: "SCHEDULED",
      driverId: DRIVER_UID,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Reject creating scheduled ride with missing required fields
  await assertFails(
    aliDb.collection("scheduled_rides").doc("SCH-BAD-2").set({
      scheduleId: "SCH-BAD-2",
      passengerId: PASSENGER_UID,
      status: "SCHEDULED",
    })
  );

  // Valid scheduled ride creation succeeds
  await assertSucceeds(
    aliDb.collection("scheduled_rides").doc("SCH-1001").set({
      scheduleId: "SCH-1001",
      passengerId: PASSENGER_UID,
      passengerName: "Ali",
      passengerPhone: "0300-1111111",
      pickupLocation: "Committee Chowk",
      destinationLocation: "Railway Station Jaranwala",
      rideType: "CAR",
      vehicleCategoryTitle: "Car Ride (AC)",
      estimatedFarePKR: 320,
      distanceKm: 3.1,
      scheduledForMillis: futureEpochMs,
      scheduledDateDisplay: "15 Nov 2026, 04:00 PM",
      notes: "Call on arrival",
      status: "SCHEDULED",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Cross-user read & cancel must FAIL
  await assertFails(sanaDb.collection("scheduled_rides").doc("SCH-1001").get());
  await assertFails(
    sanaDb.collection("scheduled_rides").doc("SCH-1001").update({
      status: "CANCELLED",
      updatedAt: nowTs(),
    })
  );

  // Owner passenger can cancel their own scheduled ride
  await assertSucceeds(
    aliDb.collection("scheduled_rides").doc("SCH-1001").update({
      status: "CANCELLED",
      updatedAt: nowTs(),
    })
  );

  // Another scheduled ride can be claimed/dispatched by approved driver
  await assertSucceeds(
    aliDb.collection("scheduled_rides").doc("SCH-1002").set({
      scheduleId: "SCH-1002",
      passengerId: PASSENGER_UID,
      passengerName: "Ali",
      passengerPhone: "0300-1111111",
      pickupLocation: "Grain Market",
      destinationLocation: "Faisalabad Clock Tower",
      rideType: "CAR",
      vehicleCategoryTitle: "Car Ride (AC)",
      estimatedFarePKR: 1850,
      distanceKm: 36.0,
      scheduledForMillis: futureEpochMs + 7200 * 1000,
      scheduledDateDisplay: "15 Nov 2026, 06:00 PM",
      notes: "",
      status: "SCHEDULED",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    approvedDriverDb.collection("scheduled_rides").doc("SCH-1002").update({
      status: "DISPATCHED",
      driverId: DRIVER_UID,
      updatedAt: nowTs(),
    })
  );
});

// =========================================================================
// 7. PICKUP & DELIVERY (PARCEL & COD) VALIDATION
// =========================================================================

test("Parcel delivery & COD bookings enforce schema bounds and approved captain dispatch", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("drivers").doc(DRIVER_UID).set({
      driverId: DRIVER_UID,
      name: "Captain Arslan",
      phone: "0300-1122334",
      vehicleType: "BIKE",
      isOnline: true,
      isApproved: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const passengerDb = testEnv.authenticatedContext(PASSENGER_UID).firestore();
  const approvedDriverDb = testEnv.authenticatedContext(DRIVER_UID).firestore();

  // Reject negative COD amount
  await assertFails(
    passengerDb.collection("ride_requests").doc("PCL-BAD-1").set({
      passengerId: PASSENGER_UID,
      pickupLocation: "Grain Market",
      destinationLocation: "Buchiana Town",
      rideType: "BIKE",
      status: "SEARCHING",
      estimatedFarePKR: 150,
      serviceMode: "PARCEL",
      isParcelDelivery: true,
      parcelDescription: "Medicines",
      recipientName: "Bilal",
      recipientPhone: "0300-1234567",
      codAmountPKR: -50,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Valid parcel delivery booking succeeds
  await assertSucceeds(
    passengerDb.collection("ride_requests").doc("PCL-1001").set({
      passengerId: PASSENGER_UID,
      pickupLocation: "Committee Chowk",
      destinationLocation: "Syedwala Adda",
      rideType: "BIKE",
      status: "SEARCHING",
      estimatedFarePKR: 220,
      distanceKm: 6.5,
      serviceMode: "PARCEL",
      isParcelDelivery: true,
      parcelDescription: "Electronic Parcel",
      parcelWeightKg: "Up to 5 kg",
      recipientName: "Usman Tariq",
      recipientPhone: "0301-5566778",
      codAmountPKR: 2500,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Approved driver can claim parcel delivery
  await assertSucceeds(
    approvedDriverDb.collection("ride_requests").doc("PCL-1001").update({
      status: "ACCEPTED",
      driverId: DRIVER_UID,
      driverName: "Captain Arslan",
      driverPhone: "0300-1122334",
      vehiclePlate: "FS-23-4412",
      updatedAt: nowTs(),
    })
  );
});

