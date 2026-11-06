"""Persist the active Wi-Fi profile's supplied secret; do not reconnect it."""
import datetime,json,os,sys
from pathlib import Path
import gi
gi.require_version('NM','1.0')
from gi.repository import NM
secret_path=Path(sys.argv[1])
try:
    password=secret_path.read_text().rstrip('\r\n')
    if not password:raise ValueError('Empty Wi-Fi credential')
    client=NM.Client.new(None)
    active=[a for a in client.get_active_connections() if a.get_connection_type()=='802-11-wireless']
    if len(active)!=1:raise ValueError('Expected exactly one active Wi-Fi profile')
    remote=active[0].get_connection()
    original=remote.to_dbus(NM.ConnectionSerializationFlags.ALL)
    name=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup=Path.home()/'.local/state/shaderlod-headless/backups'/('wifi-'+name)
    backup.mkdir(parents=True,mode=0o700)
    p=backup/'original-settings.gvariant'
    fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:f.write(original.print_(True)+'\n')
    current=NM.Client.new(None).get_connection_by_uuid(remote.get_uuid())
    if not current.to_dbus(NM.ConnectionSerializationFlags.ALL).equal(original):
        raise ValueError('Wi-Fi settings changed during backup')
    connection=remote.get_setting_connection()
    security=remote.get_setting_wireless_security()
    if security is None or security.get_key_mgmt() not in ('wpa-psk','sae'):
        raise ValueError('Expected personal WPA Wi-Fi; refusing another authentication type')
    while connection.get_num_permissions():connection.remove_permission(0)
    connection.set_property('autoconnect',True)
    security.set_property('psk',password)
    security.set_secret_flags('psk',NM.SettingSecretFlags.NONE)
    if not remote.commit_changes(True,None):raise ValueError('NetworkManager did not save settings')
    check=NM.Client.new(None).get_connection_by_uuid(remote.get_uuid())
    assert check.get_setting_connection().get_num_permissions()==0
    assert check.get_setting_connection().get_autoconnect()
    assert int(check.get_setting_wireless_security().get_property('psk-flags'))==0
    record={'backup':str(backup),'autoconnect':True,'user_permissions':[],
            'psk_flags':0,'active_connection_restarted':False,
            'limits':'Persisted settings verified; reconnect/reboot not exercised to preserve current remote access.'}
    print(json.dumps(record))
finally:
    secret_path.unlink(missing_ok=True)
