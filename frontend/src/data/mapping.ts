interface InstanceMapping {
    machineNames: Record<string, string>;
    stationCapacity?: number[];
    jobTypeOfJob: number[];
    jobTypeNames: string[];
}

const demoanlage: InstanceMapping = {
    machineNames: {
        "0": "1000 - 1190 Speicher",
        "1": "1200 Beladung",
        "2": "1210 - 1220 Abkochentfettung",
        "3": "1230 Spüle",
        "4": "1240 E-Entfettung",
        "5": "1250 Spüle Kaskade 1",
        "6": "1260 Spüle Kaskade 2",
        "7": "1270 Spüle Kaskade 3",
        "8": "1280 Beize",
        "9": "1290 Spüle Kaskade 1",
        "10": "1300 Spüle Kaskade 2",
        "11": "1310 Spüle Kaskade 3",
        "12": "1320 - 1330 Kupfer (galvanisch)",
        "13": "1340 Spüle Kaskade 1",
        "14": "1350 Spüle Kaskade 2",
        "15": "1360 Spüle Kaskade 3",
        "16": "1370 Nickel (galvanisch)",
        "17": "1380 Spüle Kaskade 1",
        "18": "1390 Spüle Kaskade 2",
        "19": "1400 Spüle Kaskade 3",
        "20": "1410 - 1460 Nickel (chemisch)",
        "21": "1470 Spüle Kaskade 1",
        "22": "1480 Spüle Kaskade 2",
        "23": "1490 Spüle Kaskade 3",
        "24": "1500 - 1520 Teflon (chemisch)",
        "25": "1530 Spüle Kaskade 1",
        "26": "1540 Spüle Kaskade 2",
        "27": "1550 Spüle Kaskade 3",
        "28": "1560 - 1580 Heißlufttrocknung",
        "29": "1590 Entladestation",
        "30": "1600 - 1620 WT-Beize",
        "31": "1630 Spüle Kaskade 1",
        "32": "1640 Spüle Kaskade 2",
        "33": "1650 Spüle Kaskade 3",
        "34": "1660 Endstation",
    },

    stationCapacity: [
        20, 1, 2, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 2, 1, 1, 1, 1, 1, 1, 1,
        6, 1, 1, 1, 3, 1, 1, 1, 3, 1,
        3, 1, 1, 1, 10,
    ],
    jobTypeOfJob: [0, 0, 0, 0, 1, 1, 2, 3, 3, 4],
    jobTypeNames: [
        "Cu + ChemNi + Tef",
        "ChemNi + Tef",
        "GalvNi + ChemNi + Tef",
        "ChemNi + Tef (kurz)",
        "GalvNi + ChemNi + Tef (lang)",
    ],
};

const mappings: Record<string, InstanceMapping> = {
    demoanlage,
    demoanlage_25: demoanlage,
    demoanlage_50: demoanlage,
    demoanlage_100: demoanlage,
};

function stationOfBath(stationCapacity: number[], bathId: number): { station: number; slot: number; capacity: number } | null {
    let offset = 0;
    for (let station = 0; station < stationCapacity.length; station++) {
        const capacity = stationCapacity[station];
        if (bathId < offset + capacity) {
            return { station, slot: bathId - offset + 1, capacity };
        }
        offset += capacity;
    }
    return null;
}

export function machineName(instanceKey: string, machineId: number, mode?: string): string {
    const mapping = mappings[instanceKey];
    if (mapping !== undefined) {
        if (mode === "FJSSP" && mapping.stationCapacity !== undefined) {
            const bath = stationOfBath(mapping.stationCapacity, machineId);
            if (bath !== null) {
                const baseName = mapping.machineNames[String(bath.station)];
                if (baseName !== undefined) {
                    return bath.capacity > 1 ? `${baseName} (Bad ${bath.slot}/${bath.capacity})` : baseName;
                }
            }
        } else {
            const name = mapping.machineNames[String(machineId)];
            if (name !== undefined) { return name; }
        }
    }
    return "Maschine " + machineId;
}

export function jobTypeOf(instanceKey: string, jobId: number): number {
    const mapping = mappings[instanceKey];
    if (mapping !== undefined && jobId < mapping.jobTypeOfJob.length) { return mapping.jobTypeOfJob[jobId]; }
    return jobId;
}

export function jobTypeName(instanceKey: string, typeIndex: number): string {
    const mapping = mappings[instanceKey];
    if (mapping !== undefined && typeIndex < mapping.jobTypeNames.length) { return mapping.jobTypeNames[typeIndex];}
    return "Typ " + (typeIndex + 1);
}