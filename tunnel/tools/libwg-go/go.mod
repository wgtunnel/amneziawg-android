module github.com/amnezia-vpn/amneziawg-android

go 1.25.1

require (
	github.com/amnezia-vpn/amneziawg-go v0.2.13
	github.com/artem-russkikh/wireproxy-awg v1.0.12
	golang.org/x/sys v0.35.0
)

require (
	github.com/MakeNowJust/heredoc/v2 v2.0.1 // indirect
	github.com/go-ini/ini v1.67.0 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/miekg/dns v1.1.68 // indirect
	github.com/tevino/abool v1.2.0 // indirect
	github.com/things-go/go-socks5 v0.0.6 // indirect
	go.uber.org/atomic v1.11.0 // indirect
	golang.org/x/crypto v0.41.0 // indirect
	golang.org/x/mod v0.26.0 // indirect
	golang.org/x/net v0.43.0 // indirect
	golang.org/x/sync v0.15.0 // indirect
	golang.org/x/time v0.12.0 // indirect
	golang.org/x/tools v0.34.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	gvisor.dev/gvisor v0.0.0-20250816201027-ba3b9ca85f20 // indirect
)

replace github.com/amnezia-vpn/amneziawg-go => github.com/wgtunnel/amneziawg-go v0.0.0-20250823024331-438e15eb711b

replace github.com/artem-russkikh/wireproxy-awg => github.com/wgtunnel/wireproxy-awg v0.0.0-20251014081251-cb0cba86c773

// local dev
//replace github.com/amnezia-vpn/amneziawg-go => ../../../../amneziawg-go
//
//replace github.com/artem-russkikh/wireproxy-awg => ../../../../wireproxy-awg
