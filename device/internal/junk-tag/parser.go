package junktag

import (
	"fmt"
	"regexp"
	"strings"
)

type Enum string

const (
	EnumBytes        Enum = "b"
	EnumCounter      Enum = "c"
	EnumTimestamp    Enum = "t"
	EnumRandomBytes  Enum = "r"
	EnumWaitTimeout  Enum = "wt"
	EnumWaitResponse Enum = "wr"
)

var validEnum = map[Enum]newGenerator{
	EnumBytes:        newBytesGenerator,
	EnumCounter:      func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumTimestamp:    newTimestampGenerator,
	EnumRandomBytes:  newRandomPacketGenerator,
	EnumWaitTimeout:  func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumWaitResponse: func(s string) (Generator, error) { return &BytesGenerator{}, nil },
}

type Foo struct {
	x []Generator
}

type Tag struct {
	Name  Enum
	Param string
}

func parseTag(input string) (Tag, error) {
	// Regular expression to match <tagname optional_param>
	re := regexp.MustCompile(`([a-zA-Z]+)(?:\s+([^>]+))?>`)

	match := re.FindStringSubmatch(input)
	tag := Tag{
		Name: Enum(match[1]),
	}
	if len(match) > 2 && match[2] != "" {
		tag.Param = strings.TrimSpace(match[2])
	}

	return tag, nil
}

func Parse(input string) (Foo, error) {
	inputSlice := strings.Split(input, "<")
	fmt.Printf("%v\n", inputSlice)
	if len(inputSlice) <= 1 {
		return Foo{}, fmt.Errorf("empty input: %s", input)
	}

	// skip byproduct of split
	inputSlice = inputSlice[1:]
	rv := Foo{x: make([]Generator, 0, len(inputSlice))}

	for _, inputParam := range inputSlice {
		if len(inputParam) <= 1 {
			return Foo{}, fmt.Errorf("empty tag in input: %s", inputSlice)
		} else if strings.Count(inputParam, ">") != 1 {
			return Foo{}, fmt.Errorf("ill formated input: %s", input)
		}

		tag, _ := parseTag(inputParam)
		fmt.Printf("Tag: %s, Param: %s\n", tag.Name, tag.Param)
		gen, ok := validEnum[tag.Name]
		if !ok {
			return Foo{}, fmt.Errorf("invalid tag: %s", tag.Name)
		}
		generator, err := gen(tag.Param)
		if err != nil {
			return Foo{}, fmt.Errorf("gen: %w", err)
		}
		rv.x = append(rv.x, generator)
	}

	return rv, nil
}
