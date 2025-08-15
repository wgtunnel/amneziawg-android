module github.com/amnezia-vpn/amneziawg-android

go 1.24.4

require (
	github.com/amnezia-vpn/amneziawg-go v0.2.13
	github.com/artem-russkikh/wireproxy-awg v1.0.12
	golang.org/x/sys v0.35.0
)

require (
	github.com/MakeNowJust/heredoc/v2 v2.0.1 // indirect
	github.com/go-ini/ini v1.67.0 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/tevino/abool v1.2.0 // indirect
	github.com/things-go/go-socks5 v0.0.6 // indirect
	go.uber.org/atomic v1.11.0 // indirect
	golang.org/x/crypto v0.41.0 // indirect
	golang.org/x/net v0.43.0 // indirect
	golang.org/x/time v0.9.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	gvisor.dev/gvisor v0.0.0-20250718015824-35000683b6d7 // indirect
)

replace github.com/amnezia-vpn/amneziawg-go => github.com/wgtunnel/amneziawg-go v0.0.0-20250809221443-1d136b0ef2d2

replace github.com/artem-russkikh/wireproxy-awg => github.com/wgtunnel/wireproxy-awg v0.0.0-20250811025000-7560393cae05
