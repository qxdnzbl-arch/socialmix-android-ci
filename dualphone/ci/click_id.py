import re, subprocess, sys, xml.etree.ElementTree as ET

xml_path, resid = sys.argv[1], sys.argv[2]
text = sys.argv[3] if len(sys.argv) > 3 else None
root = ET.parse(xml_path).getroot()
for n in root.iter("node"):
    if n.attrib.get("resource-id","").endswith("/"+resid):
        nums=list(map(int,re.findall(r"\d+",n.attrib["bounds"])))
        x=(nums[0]+nums[2])//2
        y=(nums[1]+nums[3])//2
        subprocess.check_call(["adb","shell","input","tap",str(x),str(y)])
        if text is not None:
            subprocess.check_call(["adb","shell","input","text",text])
        sys.exit(0)
raise SystemExit("missing "+resid)
